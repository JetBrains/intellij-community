/*
 * Copyright 2001-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.inspection;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ui.InspectionOptionsPanel;
import com.intellij.codeInspection.ui.RegExFormatter;
import com.intellij.codeInspection.ui.RegExInputVerifier;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.CheckBox;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.GenerateToStringContext;
import org.jetbrains.java.generate.GenerateToStringUtils;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Inspection to check if the current class overrides the toString() method.
 * <p/>
 * This inspection will use filter information from the GenerateToString plugin settings to exclude certain fields (eg. constants etc.).
 * Warns if the class has fields to be dumped and does not have a toString method.
 */
public class ClassHasNoToStringMethodInspection extends AbstractToStringInspection {

    /** User options for classes to exclude. Must be a regexp pattern */
    public String excludeClassNames = "";  // must be public for JDOMSerialization
    private Pattern excludeClassNamesPattern;

    /** User options for excluded exception classes */
    public boolean excludeException = true; // must be public for JDOMSerialization
    /** User options for excluded deprecated classes */
    public boolean excludeDeprecated = true; // must be public for JDOMSerialization
    /** User options for excluded enum classes */
    public boolean excludeEnum; // must be public for JDOMSerialization
    /** User options for excluded abstract classes */
    public boolean excludeAbstract; // must be public for JDOMSerialization

    public boolean excludeTestCode;

    public boolean excludeInnerClasses;


  @Override
    @NotNull
    public String getShortName() {
        return "ClassHasNoToStringMethod";
    }

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
            @Override
            public void visitClass(PsiClass clazz) {
                if (LOG.isDebugEnabled()) LOG.debug("checkClass: clazz=" + clazz);

                // must be a class
                final PsiIdentifier nameIdentifier = clazz.getNameIdentifier();
                if (nameIdentifier == null || clazz.getName() == null) {
                  return;
                }

                if (excludeException && InheritanceUtil.isInheritor(clazz, CommonClassNames.JAVA_LANG_THROWABLE)) {
                    return;
                }
                if (excludeDeprecated && clazz.isDeprecated()) {
                    return;
                }
                if (excludeEnum && clazz.isEnum()) {
                    return;
                }
                if (excludeAbstract && clazz.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    return;
                }
                if (excludeTestCode && TestFrameworks.getInstance().isTestClass(clazz)) {
                    return;
                }
                if (excludeInnerClasses && clazz.getContainingClass() != null) {
                    return;
                }

                // if it is an excluded class - then skip
                if (excludeClassNamesPattern != null) {
                    try {
                        String name = clazz.getName();
                        if (name != null && excludeClassNamesPattern.matcher(name).matches()) {
                            return;
                        }
                    } catch (PatternSyntaxException ignore) {}
                }

                // must have fields
                PsiField[] fields = clazz.getFields();
                if (fields.length == 0) {
                    return;
                }

                // get list of fields and getter methods supposed to be dumped in the toString method
                fields = GenerateToStringUtils.filterAvailableFields(clazz, GenerateToStringContext.getConfig().getFilterPattern());
                PsiMethod[] methods = null;
                if (GenerateToStringContext.getConfig().isEnableMethods()) {
                    // okay 'getters in code generation' is enabled so check
                    methods = GenerateToStringUtils.filterAvailableMethods(clazz, GenerateToStringContext.getConfig().getFilterPattern());
                }

                // there should be any fields
                if (Math.max(fields.length, methods == null ? 0 : methods.length) == 0) {
                  return;
                }

                // okay some fields/getter methods are supposed to dumped, does a toString method exist
                final PsiMethod[] toStringMethods = clazz.findMethodsByName("toString", false);
                for (PsiMethod method : toStringMethods) {
                    final PsiParameterList parameterList = method.getParameterList();
                    if (parameterList.isEmpty()) {
                        // toString() method found
                        return;
                    }
                }
                final PsiMethod[] superMethods = clazz.findMethodsByName("toString", true);
                for (PsiMethod method : superMethods) {
                    final PsiParameterList parameterList = method.getParameterList();
                    if (!parameterList.isEmpty()) {
                        continue;
                    }
                    if (method.hasModifierProperty(PsiModifier.FINAL)) {
                        // final toString() in super class found
                        return;
                    }
                }
                holder.registerProblem(nameIdentifier,
                                       JavaAnalysisBundle.message("inspection.class.has.no.to.string.method.description", clazz.getName()),
                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING, createFixes());
            }
        };
    }

    /**
     * Creates the options panel in the settings for user changeable options.
     *
     * @return the options panel
     */
    @Override
    public JComponent createOptionsPanel() {
        final InspectionOptionsPanel panel = new InspectionOptionsPanel();

        final JFormattedTextField excludeClassNamesField = new JFormattedTextField(new RegExFormatter());
        excludeClassNamesField.setValue(excludeClassNamesPattern);
        excludeClassNamesField.setColumns(25);
        excludeClassNamesField.setInputVerifier(new RegExInputVerifier());
        excludeClassNamesField.setFocusLostBehavior(JFormattedTextField.COMMIT);
        excludeClassNamesField.setMinimumSize(excludeClassNamesField.getPreferredSize());
        UIUtil.fixFormattedField(excludeClassNamesField);
        Document document = excludeClassNamesField.getDocument();
        document.addDocumentListener(new DocumentAdapter() {

            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                try {
                    excludeClassNamesField.commitEdit();
                    excludeClassNamesPattern = (Pattern)excludeClassNamesField.getValue();
                    excludeClassNames = excludeClassNamesPattern.pattern();
                } catch (final Exception ignore) {}
            }
        });

        panel.addRow(new JLabel(JavaAnalysisBundle.message("inspection.class.has.no.to.string.method.exclude.classes.reg.exp.option")), excludeClassNamesField);

        final CheckBox excludeExceptionCheckBox = new CheckBox(
          JavaAnalysisBundle.message("inspection.class.has.no.to.string.method.ignore.exception.classes.option"), this, "excludeException");

        panel.add(excludeExceptionCheckBox);

        final CheckBox excludeDeprecatedCheckBox = new CheckBox(
          JavaAnalysisBundle.message("inspection.class.has.no.to.string.method.ignore.deprecated.classes.option"), this, "excludeDeprecated");
        panel.add(excludeDeprecatedCheckBox);

        final CheckBox excludeEnumCheckBox = new CheckBox(
          JavaAnalysisBundle.message("inspection.class.has.no.to.string.method.ignore.enum.classes.option"), this, "excludeEnum");
        panel.add(excludeEnumCheckBox);

        final CheckBox excludeAbstractCheckBox = new CheckBox(
          JavaAnalysisBundle.message("inspection.class.has.no.to.string.method.ignore.abstract.classes.option"), this, "excludeAbstract");
        panel.add(excludeAbstractCheckBox);

        final CheckBox excludeInTestCodeCheckBox = new CheckBox(
          JavaAnalysisBundle.message("inspection.class.has.no.to.string.method.ignore.test.classes.option"), this, "excludeTestCode");
        panel.add(excludeInTestCodeCheckBox);

        final CheckBox excludeInnerClasses = new CheckBox(
          JavaAnalysisBundle.message("inspection.class.has.no.to.string.method.ignore.inner.classes.option"), this, "excludeInnerClasses");
        panel.add(excludeInnerClasses);

        return panel;
    }

    @Override
    public void readSettings(@NotNull Element node) {
        super.readSettings(node);
        try {
            excludeClassNamesPattern = Pattern.compile(excludeClassNames);
        }
        catch (PatternSyntaxException ignored) { }
    }
}
