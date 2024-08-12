// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.naming;

import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptCheckboxPanel;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ExtensionPointUtil;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.SyntheticElement;
import com.intellij.serialization.SerializationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * Abstract class for naming convention inspections. Base inspection expects {@link NamingConvention} extensions which are processed one by one,
 * the first which returns true from {@link NamingConvention#isApplicable(PsiNameIdentifierOwner)}, wins and provides bean to check the member name.
 *
 * Provide {@link #createRenameFix()} to register rename fix.
 * Register {@link AbstractNamingConventionMerger} to provide settings migration from multiple inspections to compound one
 */
public abstract class AbstractNamingConventionInspection<T extends PsiNameIdentifierOwner> extends LocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(AbstractNamingConventionInspection.class);

  private final Map<String, NamingConvention<T>> myNamingConventions = new LinkedHashMap<>();
  private final Map<String, NamingConventionBean> myNamingConventionBeans = new LinkedHashMap<>();
  private final Map<String, Element> myUnloadedElements = new LinkedHashMap<>();
  private final Set<String> myDisabledShortNames = new HashSet<>();
  private final @Nullable String myDefaultConventionShortName;

  protected AbstractNamingConventionInspection(Iterable<? extends NamingConvention<T>> extensions, final @Nullable String defaultConventionShortName) {
    for (NamingConvention<T> convention : extensions) {
      registerConvention(convention);
    }
    myDefaultConventionShortName = defaultConventionShortName;
  }

  protected void registerConvention(NamingConvention<T> convention) {
    String shortName = convention.getShortName();
    NamingConvention<T> oldConvention = myNamingConventions.put(shortName, convention);
    if (oldConvention != null) {
      LOG.error("Duplicated short names: " + shortName + " first: " + oldConvention + "; second: " + convention);
    }
    myNamingConventionBeans.put(shortName, convention.createDefaultBean());
    if (!convention.isEnabledByDefault()) {
      myDisabledShortNames.add(shortName);
    }
  }

  protected void unregisterConvention(@NotNull NamingConvention<T> extension) {
    String shortName = extension.getShortName();
    Element element = writeConvention(shortName, extension);
    if (element != null) {
      myUnloadedElements.put(shortName, element);
    }
    myNamingConventionBeans.remove(shortName);
    myNamingConventions.remove(shortName);
    myDisabledShortNames.remove(shortName);
  }

  protected void registerConventionsListener(@NotNull ExtensionPointName<NamingConvention<T>> epName) {
    Disposable disposable = ExtensionPointUtil.createExtensionDisposable(
      this,
      LocalInspectionEP.LOCAL_INSPECTION.getPoint(),
      inspectionEP -> this.getClass().getName().equals(inspectionEP.implementationClass)
    );

    epName.addExtensionPointListener(new ExtensionPointListener<>() {
      @Override
      public void extensionAdded(@NotNull NamingConvention<T> extension, @NotNull PluginDescriptor pluginDescriptor) {
        registerConvention(extension);
      }

      @Override
      public void extensionRemoved(@NotNull NamingConvention<T> extension, @NotNull PluginDescriptor pluginDescriptor) {
        unregisterConvention(extension);
      }
    }, disposable);
  }

  protected abstract @Nullable LocalQuickFix createRenameFix();

  private void initDisabledState() {
    myDisabledShortNames.clear();
    for (NamingConvention<T> convention : myNamingConventions.values()) {
      if (!convention.isEnabledByDefault()) {
        myDisabledShortNames.add(convention.getShortName());
      }
    }
  }

  public NamingConventionBean getNamingConventionBean(String shortName) {
    return myNamingConventionBeans.get(shortName);
  }

  public Set<String> getOldToolNames() {
    return myNamingConventions.keySet();
  }

  protected @NotNull @InspectionMessage String createErrorMessage(String name, String shortName) {
    return myNamingConventions.get(shortName).createErrorMessage(name, myNamingConventionBeans.get(shortName));
  }

  @Override
  public void readSettings(@NotNull Element node) {
    initDisabledState();
    for (Element extension : node.getChildren("extension")) {
      String shortName = extension.getAttributeValue("name");
      if (shortName == null) continue;
      NamingConventionBean conventionBean = myNamingConventionBeans.get(shortName);
      if (conventionBean == null) {
        myUnloadedElements.put(shortName, extension);
        continue;
      }
      try {
        XmlSerializer.deserializeInto(conventionBean, extension);
        conventionBean.initPattern();
      }
      catch (SerializationException e) {
        throw new InvalidDataException(e);
      }
      if (Boolean.parseBoolean(extension.getAttributeValue("enabled"))) {
        myDisabledShortNames.remove(shortName);
      } else {
        myDisabledShortNames.add(shortName);
      }
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    Set<String> shortNames = new TreeSet<>(myNamingConventions.keySet());
    shortNames.addAll(myUnloadedElements.keySet());
    for (String shortName : shortNames) {
      NamingConvention<T> convention = myNamingConventions.get(shortName);
      if (convention == null) {
        Element element = myUnloadedElements.get(shortName);
        if (element != null) node.addContent(element.clone());
        continue;
      }
      Element element = writeConvention(shortName, convention);
      if (element == null) continue;
      node.addContent(element);
    }
  }

  private Element writeConvention(String shortName, NamingConvention<T> convention) {
    boolean disabled = myDisabledShortNames.contains(shortName);
    Element element = new Element("extension")
      .setAttribute("name", shortName)
      .setAttribute("enabled", disabled ? "false" : "true");
    NamingConventionBean conventionBean = myNamingConventionBeans.get(shortName);
    if (!convention.createDefaultBean().equals(conventionBean)) {
      XmlSerializer.serializeInto(conventionBean, element);
    }
    else {
      if (disabled != convention.isEnabledByDefault()) return null;
    }
    return element;
  }

  public boolean isConventionEnabled(String shortName) {
    return !myDisabledShortNames.contains(shortName);
  }

  protected void checkName(@NotNull T member, @NotNull String name, @NotNull ProblemsHolder holder) {
    if (member instanceof SyntheticElement) return;
    checkName(member, shortName -> {
      LocalQuickFix[] fixes;
      if (holder.isOnTheFly()) {
        LocalQuickFix fix = createRenameFix();
        fixes = fix != null ? new LocalQuickFix[]{ fix } : null;
      }
      else {
        fixes = null;
      }
      PsiElement element = ObjectUtils.notNull(member.getNameIdentifier(), member);
      if (!element.isPhysical()) {
        element = element.getNavigationElement();
      }
      holder.registerProblem(element, createErrorMessage(name, shortName), fixes);
    });
  }

  protected void checkName(@NotNull T member, @NotNull Consumer<? super String> errorRegister) {
    for (NamingConvention<T> namingConvention : myNamingConventions.values()) {
      if (namingConvention.isApplicable(member)) {
        String shortName = namingConvention.getShortName();
        if (myDisabledShortNames.contains(shortName)) {
          break;
        }
        NamingConventionBean activeBean = myNamingConventionBeans.get(shortName);
        if (activeBean instanceof NamingConventionWithFallbackBean && ((NamingConventionWithFallbackBean)activeBean).isInheritDefaultSettings()) {
          LOG.assertTrue(myDefaultConventionShortName != null, activeBean + " expects that default conversion is configured");
          shortName = myDefaultConventionShortName;
          //disabled when fallback is disabled
          if (myDisabledShortNames.contains(shortName)) {
            break;
          }

          activeBean = myNamingConventionBeans.get(shortName);
          namingConvention = myNamingConventions.get(shortName);
        }
        if (!namingConvention.isValid(member, activeBean)) {
          errorRegister.accept(shortName);
        }
        break;
      }
    }
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    List<NamingConvention<T>> values = new ArrayList<>(myNamingConventions.values());
    Collections.reverse(values);
    return OptPane.pane(new OptCheckboxPanel(ContainerUtil.map(values, convention -> {
      String shortName = convention.getShortName();
      NamingConventionBean bean = myNamingConventionBeans.get(shortName);
      //noinspection LanguageMismatch
      return bean.getOptionsPane().prefix(shortName).asCheckbox(shortName, convention.getElementDescription());
    })));
  }

  @Override
  public @NotNull OptionController getOptionController() {
    OptionController controller = OptionController.of(
      shortName -> !myDisabledShortNames.contains(shortName),
      (shortName, value) -> setEnabled((boolean)value, shortName)
    );
    for (Map.Entry<String, NamingConventionBean> entry : myNamingConventionBeans.entrySet()) {
      controller = controller.onPrefix(entry.getKey(), entry.getValue().getOptionController());
    }
    return controller;
  }

  public void setEnabled(boolean value, String conventionShortName) {
    if (value) {
      myDisabledShortNames.remove(conventionShortName);
    }
    else {
      myDisabledShortNames.add(conventionShortName);
    }
  }
}
