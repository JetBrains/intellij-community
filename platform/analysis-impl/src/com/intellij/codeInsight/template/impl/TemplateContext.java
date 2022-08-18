// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.template.LiveTemplateContext;
import com.intellij.codeInsight.template.LiveTemplateContextService;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.util.JdomKt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.jvm.functions.Function0;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TemplateContext {
  private final Map<String, Boolean> myContextStates = new HashMap<>();
  private final LiveTemplateContextService service = LiveTemplateContextService.getInstance();

  public TemplateContext createCopy() {
    TemplateContext cloneResult = new TemplateContext();
    cloneResult.myContextStates.putAll(myContextStates);
    return cloneResult;
  }

  @Nullable LiveTemplateContext getDifference(@NotNull TemplateContext defaultContext) {
    return ContainerUtil.find(service.getLiveTemplateContexts(),
                              type -> isEnabled(type) != defaultContext.isEnabled(type));
  }

  @Nullable TemplateContextType getDifferenceType(@NotNull TemplateContext defaultContext) {
    LiveTemplateContext differenceExtension = getDifference(defaultContext);
    if (differenceExtension != null) {
      return differenceExtension.getTemplateContextType();
    }
    return null;
  }

  public boolean isEnabled(@NotNull TemplateContextType contextType) {
    synchronized (myContextStates) {
      return isEnabledNoSync(contextType.getContextId());
    }
  }

  private boolean isEnabled(@NotNull LiveTemplateContext contextType) {
    synchronized (myContextStates) {
      return isEnabledNoSync(contextType.getContextId());
    }
  }

  private boolean isEnabledNoSync(@NotNull String contextTypeId) {
    Boolean storedValue = getOwnValueNoSync(contextTypeId);
    if (storedValue == null) {
      LiveTemplateContext liveTemplateContext = getLiveTemplateContext(contextTypeId);
      String baseContextTypeId = liveTemplateContext != null ? liveTemplateContext.getBaseContextId() : null;
      return baseContextTypeId != null && isEnabledNoSync(baseContextTypeId);
    }
    return storedValue.booleanValue();
  }

  public @Nullable Boolean getOwnValue(@NotNull TemplateContextType contextType) {
    return getOwnValue(contextType.getContextId());
  }

  private @Nullable Boolean getOwnValue(String contextTypeId) {
    synchronized (myContextStates) {
      return getOwnValueNoSync(contextTypeId);
    }
  }

  private @Nullable Boolean getOwnValueNoSync(String contextTypeId) {
    return myContextStates.get(contextTypeId);
  }

  private @Nullable LiveTemplateContext getLiveTemplateContext(@Nullable String contextTypeId) {
    if (contextTypeId == null) return null;

    return service.getLiveTemplateContext(contextTypeId);
  }

  public void setEnabled(TemplateContextType contextType, boolean value) {
    synchronized (myContextStates) {
      myContextStates.put(contextType.getContextId(), value);
    }
  }

  // used during initialization => no sync
  @VisibleForTesting
  public void setDefaultContext(@NotNull TemplateContext defContext) {
    Map<String, Boolean> copy = new HashMap<>(myContextStates);
    myContextStates.clear();
    myContextStates.putAll(defContext.myContextStates);
    myContextStates.putAll(copy);
  }

  // used during initialization => no sync
  @VisibleForTesting
  public void readTemplateContext(@NotNull Element element) {
    Map<String, String> internMap = service.getInternalIds();
    for (Element option : element.getChildren("option")) {
      String name = option.getAttributeValue("name");
      String value = option.getAttributeValue("value");
      if (name != null && value != null) {
        myContextStates.put(ContainerUtil.getOrElse(internMap, name, name), Boolean.parseBoolean(value));
      }
    }

    myContextStates.putAll(makeInheritanceExplicit());
  }

  /**
   * Mark contexts explicitly as excluded which are excluded because some of their bases is explicitly marked as excluded.
   * Otherwise, that `excluded` status will be forgotten if the base context is enabled.
   */
  private @NotNull Map<String, Boolean> makeInheritanceExplicit() {
    Map<String, Boolean> explicitStates = new HashMap<>();
    for (LiveTemplateContext type : service.getLiveTemplateContexts()) {
      if (isDisabledByInheritance(type)) {
        explicitStates.put(type.getContextId(), false);
      }
    }
    return explicitStates;
  }

  private boolean isDisabledByInheritance(LiveTemplateContext type) {
    return !hasOwnValue(type) &&
           !isEnabled(type) &&
           JBIterable.generate(type, context -> getLiveTemplateContext(context.getBaseContextId())).filter(this::hasOwnValue).first() != null;
  }

  private boolean hasOwnValue(LiveTemplateContext t) {
    return getOwnValue(t.getContextId()) != null;
  }

  @TestOnly
  public Element writeTemplateContext(@Nullable TemplateContext defaultContext) {
    return writeTemplateContext(defaultContext, getIdToType());
  }

  @VisibleForTesting
  @Nullable
  public Element writeTemplateContext(@Nullable TemplateContext defaultContext, @NotNull Lazy<? extends Map<String, TemplateContextType>> idToType) {
    if (myContextStates.isEmpty()) {
      return null;
    }

    Element element = new Element(TemplateConstants.CONTEXT);
    List<Map.Entry<String, Boolean>> entries = new ArrayList<>(myContextStates.entrySet());
    entries.sort(Map.Entry.comparingByKey());
    for (Map.Entry<String, Boolean> entry : entries) {
      Boolean ownValue = entry.getValue();
      if (ownValue == null) {
        continue;
      }

      TemplateContextType type = idToType.getValue().get(entry.getKey());
      if (type == null) {
        // https://youtrack.jetbrains.com/issue/IDEA-155623#comment=27-1721029
        JdomKt.addOptionTag(element, entry.getKey(), ownValue.toString());
      }
      else if (isValueChanged(ownValue, type, defaultContext)) {
        JdomKt.addOptionTag(element, type.getContextId(), ownValue.toString());
      }
    }
    return element;
  }

  @NotNull
  public static Lazy<Map<String, TemplateContextType>> getIdToType() {
    return LazyKt.lazy(new Function0<>() {
      @Override
      public Map<String, TemplateContextType> invoke() {
        Map<String, TemplateContextType> idToType = new HashMap<>();
        for (LiveTemplateContext type : LiveTemplateContextService.getInstance().getLiveTemplateContexts()) {
          idToType.put(type.getContextId(), type.getTemplateContextType());
        }
        return idToType;
      }
    });
  }

  /**
   * Default value for GROOVY_STATEMENT is `true` (defined in the `plugins/groovy/groovy-psi/resources/liveTemplates/Groovy.xml`).
   * Base value is `false`.
   * <p>
   * If default value is defined (as in our example)  we must not take base value in account.
   * Because on init `setDefaultContext` will be called, and we will have own value.
   * Otherwise, it will be not possible to set value for `GROOVY_STATEMENT` neither to `true` (equals to default), nor to `false` (equals to base).
   * See TemplateSchemeTest.
   */
  private boolean isValueChanged(@NotNull Boolean ownValue, @NotNull TemplateContextType type, @Nullable TemplateContext defaultContext) {
    Boolean defaultValue = defaultContext == null ? null : defaultContext.getOwnValue(type.getContextId());
    if (defaultValue == null) {
      TemplateContextType base = type.getBaseContextType();
      boolean baseEnabled = base != null && isEnabled(base);
      return ownValue != baseEnabled;
    }
    return !ownValue.equals(defaultValue);
  }

  @Override
  public String toString() {
    return myContextStates.toString();
  }
}
