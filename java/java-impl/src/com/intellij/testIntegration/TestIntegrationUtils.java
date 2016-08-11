/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testIntegration;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;

public class TestIntegrationUtils {
  private static final Logger LOG = Logger.getInstance("#" + TestIntegrationUtils.class.getName());

  public enum MethodKind {
    SET_UP("setUp") {
      public FileTemplateDescriptor getFileTemplateDescriptor(@NotNull TestFramework framework) {
        return framework.getSetUpMethodFileTemplateDescriptor();
      }
    },
    TEAR_DOWN("tearDown") {
      public FileTemplateDescriptor getFileTemplateDescriptor(@NotNull TestFramework framework) {
        return framework.getTearDownMethodFileTemplateDescriptor();
      }
    },
    TEST("test") {
      public FileTemplateDescriptor getFileTemplateDescriptor(@NotNull TestFramework framework) {
        return framework.getTestMethodFileTemplateDescriptor();
      }
    },
    DATA("data") {
      @Override
      public FileTemplateDescriptor getFileTemplateDescriptor(@NotNull TestFramework framework) {
        if (framework instanceof JavaTestFramework) {
          return ((JavaTestFramework)framework).getParametersMethodFileTemplateDescriptor();
        }
        return null;
      }
    },
    TEST_CLASS("testClass") {
      @Override
      public FileTemplateDescriptor getFileTemplateDescriptor(@NotNull TestFramework framework) {
        if (framework instanceof JavaTestFramework) {
          return ((JavaTestFramework)framework).getTestClassFileTemplateDescriptor();
        }
        return null;
      }
    };
    private final String myDefaultName;

    MethodKind(String defaultName) {
      myDefaultName = defaultName;
    }

    public String getDefaultName() {
      return myDefaultName;
    }

    public abstract FileTemplateDescriptor getFileTemplateDescriptor(@NotNull TestFramework framework);
  }

  public static boolean isTest(@NotNull PsiElement element) {
    PsiClass klass = findOuterClass(element);
    return klass != null && TestFrameworks.getInstance().isTestClass(klass);
  }

  @Nullable
  public static PsiClass findOuterClass(@NotNull PsiElement element) {
    PsiClass result = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    if (result == null) {
       final PsiFile containingFile = element.getContainingFile();
       if (containingFile instanceof PsiClassOwner){
        final PsiClass[] classes = ((PsiClassOwner)containingFile).getClasses();
        if (classes.length == 1) {
          result = classes[0];
        }
      }
    }
    if (result == null) return null;
    do {
      PsiClass nextParent = PsiTreeUtil.getParentOfType(result, PsiClass.class, true);
      if (nextParent == null) return result;
      result = nextParent;
    }
    while (true);
  }

  public static List<MemberInfo> extractClassMethods(PsiClass clazz, boolean includeInherited) {
    List<MemberInfo> result = new ArrayList<>();

    do {
      MemberInfo.extractClassMembers(clazz, result, new MemberInfo.Filter<PsiMember>() {
        public boolean includeMember(PsiMember member) {
          if (!(member instanceof PsiMethod)) return false;
          PsiModifierList list = member.getModifierList();
          return !list.hasModifierProperty(PsiModifier.PRIVATE);
        }
      }, false);
      clazz = clazz.getSuperClass();
    }
    while (clazz != null
           && clazz.getSuperClass() != null // not the Object
           && includeInherited);

    return result;
  }

  public static void runTestMethodTemplate(@NotNull MethodKind methodKind,
                                           TestFramework framework,
                                           final Editor editor,
                                           final PsiClass targetClass,
                                           final PsiMethod method,
                                           @Nullable String name,
                                           boolean automatic, Set<String> existingNames) {
    runTestMethodTemplate(methodKind, framework, editor, targetClass, null, method, name, automatic, existingNames);
  }

  public static void runTestMethodTemplate(@NotNull MethodKind methodKind,
                                           TestFramework framework,
                                           final Editor editor,
                                           final PsiClass targetClass,
                                           @Nullable PsiClass sourceClass,
                                           final PsiMethod method,
                                           @Nullable String name,
                                           boolean automatic,
                                           Set<String> existingNames) {
    runTestMethodTemplate(editor, targetClass, method, automatic,
                          createTestMethodTemplate(methodKind, framework, targetClass, sourceClass, name, automatic, existingNames));
  }

  public static void runTestMethodTemplate(final Editor editor,
                                           final PsiClass targetClass,
                                           final PsiMethod method,
                                           boolean automatic, final Template template) {

    final int startOffset = method.getModifierList().getTextRange().getStartOffset();
    final TextRange range = new TextRange(startOffset, method.getTextRange().getEndOffset());
    editor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), "");
    editor.getCaretModel().moveToOffset(range.getStartOffset());

    final Project project = targetClass.getProject();

    TemplateEditingAdapter adapter = null;

    if (!automatic) {
      adapter = new TemplateEditingAdapter() {
        @Override
        public void templateFinished(Template template, boolean brokenOff) {
          ApplicationManager.getApplication().runWriteAction(() -> {
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
            PsiFile psi = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
            PsiElement el = PsiTreeUtil.findElementOfClassAtOffset(psi, editor.getCaretModel().getOffset() - 1, PsiMethod.class, false);

            if (el != null) {
              PsiMethod method1 = PsiTreeUtil.getParentOfType(el, PsiMethod.class, false);
              if (method1 != null) {
                if (method1.findDeepestSuperMethods().length > 0) {
                  GenerateMembersUtil.setupGeneratedMethod(method1);
                }
                CreateFromUsageUtils.setupEditor(method1, editor);
              }
            }
          });
        }
      };
    }

    TemplateManager.getInstance(project).startTemplate(editor, template, adapter);
  }

  public static Template createTestMethodTemplate(@NotNull MethodKind methodKind,
                                                  TestFramework descriptor,
                                                  @NotNull PsiClass targetClass,
                                                  @Nullable String name,
                                                  boolean automatic,
                                                  Set<String> existingNames) {
    return createTestMethodTemplate(methodKind, descriptor, targetClass, null, name, automatic, existingNames);
  }

  public static Template createTestMethodTemplate(@NotNull MethodKind methodKind,
                                                  TestFramework descriptor,
                                                  @NotNull PsiClass targetClass,
                                                  @Nullable PsiClass sourceClass,
                                                  @Nullable String name,
                                                  boolean automatic,
                                                  Set<String> existingNames) {
    FileTemplateDescriptor templateDesc = methodKind.getFileTemplateDescriptor(descriptor);
    String templateName = templateDesc.getFileName();
    FileTemplate fileTemplate = FileTemplateManager.getInstance(targetClass.getProject()).getCodeTemplate(templateName);
    Template template = TemplateManager.getInstance(targetClass.getProject()).createTemplate("", "");

    String templateText;
    try {
      Properties properties = new Properties();
      if (sourceClass != null && sourceClass.isValid()) {
        properties.setProperty(FileTemplate.ATTRIBUTE_CLASS_NAME, sourceClass.getQualifiedName());
      }

      templateText = fileTemplate.getText(properties);
    }
    catch (IOException e) {
      LOG.warn(e);
      templateText = fileTemplate.getText();
    }

    if (name == null) name = methodKind.getDefaultName();

    if (existingNames != null && !existingNames.add(name)) {
      int idx = 1;
      while (existingNames.contains(name)) {
        final String newName = name + (idx++);
        if (existingNames.add(newName)) {
          name = newName;
          break;
        }
      }
    }

    templateText = StringUtil.replace(templateText, "${BODY}", "");

    int from = 0;
    while (true) {
      int index = templateText.indexOf("${NAME}", from);
      if (index == -1) break;

      template.addTextSegment(templateText.substring(from, index));

      if (index > 0 && !Character.isWhitespace(templateText.charAt(index - 1))) {
        name = StringUtil.capitalize(name);
      }
      else {
        name = StringUtil.decapitalize(name);
      }
      if (from == 0) {
        Expression nameExpr = new ConstantNode(name);
        template.addVariable("name", nameExpr, nameExpr, !automatic);
      }
      else {
        template.addVariableSegment("name");
      }

      from = index + "${NAME}".length();
    }
    template.addTextSegment(templateText.substring(from, templateText.length()));

    template.setToIndent(true);
    template.setToReformat(true);
    template.setToShortenLongNames(true);

    return template;
  }

  public static PsiMethod createDummyMethod(@NotNull PsiElement context) {
    JVMElementFactory factory = JVMElementFactories.getFactory(context.getLanguage(), context.getProject());
    if (factory == null) factory = JavaPsiFacade.getElementFactory(context.getProject());
    return factory.createMethod("dummy", PsiType.VOID);
  }

  public static List<TestFramework> findSuitableFrameworks(PsiClass targetClass) {
    TestFramework[] frameworks = Extensions.getExtensions(TestFramework.EXTENSION_NAME);
    for (TestFramework each : frameworks) {
      if (each.isTestClass(targetClass)) {
        return Collections.singletonList(each);
      }
    }

    List<TestFramework> result = new SmartList<>();
    for (TestFramework each : frameworks) {
      if (each.isPotentialTestClass(targetClass)) {
        result.add(each);
      }
    }
    return result;
  }

  private TestIntegrationUtils() {
  }
}
