// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.options.OptTable;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.codeInspection.options.RegexValidator;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static com.intellij.codeInspection.options.OptPane.column;
import static com.intellij.codeInspection.options.OptPane.table;

/**
 * Remember to call readSettings() and writeSettings from your inspection class!
 * @author Bas Leijdekkers
 */
public class MethodMatcher implements OptionContainer {

  private final List<String> myMethodNamePatterns = new ArrayList<>();
  private final List<String> myClassNames = new ArrayList<>();
  private final Map<String, Pattern> myPatternCache = new HashMap<>();
  private final boolean myWriteDefaults;
  private final String myOptionName;
  private String myDefaultSettings = null;

  public MethodMatcher() {
    this(false, "METHOD_MATCHER_CONFIG");
  }

  public MethodMatcher(boolean writeDefaults, @NonNls String optionName) {
    myWriteDefaults = writeDefaults;
    myOptionName = optionName;
  }

  public MethodMatcher add(@NonNls @NotNull String className, @NonNls @NotNull String methodNamePattern) {
    myClassNames.add(className);
    myMethodNamePatterns.add(methodNamePattern);
    return this;
  }

  public void add(@NotNull PsiMethodCallExpression expression) {
    final PsiMethod method = expression.resolveMethod();
    if (method != null) {
      add(method);
    }
  }

  public void add(@NotNull PsiMethod method) {
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return;
    }
    final String fqName = aClass.getQualifiedName();
    final int index = myClassNames.indexOf(fqName);
    final String methodName = method.getName();
    if (index < 0) {
      myClassNames.add(fqName);
      myMethodNamePatterns.add(methodName);
    }
    else {
      final String pattern = myMethodNamePatterns.get(index);
      if (pattern.isEmpty()) {
        myMethodNamePatterns.set(index, methodName);
        return;
      }
      else if (".*".equals(pattern)) {
        return;
      }
      final String[] names = pattern.split("\\|");
      for (String name : names) {
        if (methodName.equals(name)) {
          return;
        }
      }
      myMethodNamePatterns.set(index, pattern + '|' + methodName);
    }
    ProjectInspectionProfileManager.getInstance(method.getProject()).fireProfileChanged();
  }

  protected @NotNull String getOptionName() {
    return myOptionName;
  }

  public List<String> getMethodNamePatterns() {
    return myMethodNamePatterns;
  }

  public List<String> getClassNames() {
    return myClassNames;
  }

  public boolean matches(@Nullable PsiMethod method) {
    return find(method, null,false) != -1;
  }

  /**
   * Finds the index of the method in the list of name patterns and class names.
   *
   * @param method           The method to find.
   * @param containingClass  The class that contains the method, if it is null a class, which contains method, wil be used
   * @param checkBases       Flag indicating whether to check the method in the base class and its subclasses.
   * @return The index of the method, or -1 if not found.
   */
  public int find(@Nullable PsiMethod method, @Nullable PsiClass containingClass, boolean checkBases) {
    if (method == null) {
      return -1;
    }
    final String methodName = method.getName();
    if (containingClass == null) {
      containingClass = method.getContainingClass();
    }
    if (containingClass == null) {
      return -1;
    }
    for (int i = 0, size = myMethodNamePatterns.size(); i < size; i++) {
      final Pattern pattern = getPattern(i);
      if (pattern == null || !pattern.matcher(methodName).matches()) {
        continue;
      }
      final String className = myClassNames.get(i);
      final PsiClass base = JavaPsiFacade.getInstance(method.getProject()).findClass(className, containingClass.getResolveScope());
      if (InheritanceUtil.isInheritorOrSelf(containingClass, base, true)) {
        if (base.findMethodsBySignature(method, checkBases).length > 0) {
          // is method present in base class and not introduced in some subclass
          return i;
        }
      }
    }
    return -1;
  }

  public boolean matches(PsiCall call) {
    return matches(call.resolveMethod());
  }

  private Pattern getPattern(int i) {
    final String methodNamePattern = myMethodNamePatterns.get(i);
    if (StringUtil.isEmpty(methodNamePattern)) {
      return null;
    }
    Pattern pattern = myPatternCache.get(methodNamePattern);
    if (pattern == null) {
      try {
        pattern = Pattern.compile(methodNamePattern);
        myPatternCache.put(methodNamePattern, pattern);
      }
      catch (PatternSyntaxException | NullPointerException ignore) {
        return null;
      }
    }
    return pattern;
  }

  public MethodMatcher finishDefault() {
    if (myDefaultSettings != null) throw new IllegalStateException();
    myDefaultSettings = BaseInspection.formatString(myClassNames, myMethodNamePatterns);
    return this;
  }

  public void readSettings(@NotNull Element node) throws InvalidDataException {
    String settings = null;
    for (Element option : node.getChildren("option")) {
      final String value = option.getAttributeValue("name");
      if (value != null && value.equals(getOptionName())) {
        settings = option.getAttributeValue("value");
        break;
      }
    }
    if (settings == null) return;
    myPatternCache.clear();
    myClassNames.clear();
    myMethodNamePatterns.clear();
    BaseInspection.parseString(settings, myClassNames, myMethodNamePatterns);
  }

  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    final String settings = BaseInspection.formatString(myClassNames, myMethodNamePatterns);
    if (!myWriteDefaults && settings.equals(myDefaultSettings)) return;
    node.addContent(new Element("option").setAttribute("name", getOptionName()).setAttribute("value", settings));
  }

  /**
   * @param label display label for the control 
   * @return control to edit options of this matcher
   */
  public @NotNull OptTable getTable(@NotNull @NlsContexts.Label String label) {
    return table(label,
                 column("myClassNames", InspectionGadgetsBundle.message("result.of.method.call.ignored.class.column.title"),
                            new JavaClassValidator()),
                 column("myMethodNamePatterns", InspectionGadgetsBundle.message("method.name.regex"),
                            new RegexValidator()));
  }
}
