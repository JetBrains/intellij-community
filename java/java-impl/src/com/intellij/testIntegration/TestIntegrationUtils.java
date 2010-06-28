/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.TestUtil;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class TestIntegrationUtils {
  private static final Logger LOG = Logger.getInstance("#" + TestIntegrationUtils.class.getName());

  public enum MethodKind {
    SET_UP("setUp") {
      public FileTemplateDescriptor getFileTemplateDescriptor(TestFramework framework) {
        return framework.getSetUpMethodFileTemplateDescriptor();
      }
    },
    TEAR_DOWN("tearDown") {
      public FileTemplateDescriptor getFileTemplateDescriptor(TestFramework framework) {
        return framework.getTearDownMethodFileTemplateDescriptor();
      }
    },
    TEST("test") {
      public FileTemplateDescriptor getFileTemplateDescriptor(TestFramework framework) {
        return framework.getTestMethodFileTemplateDescriptor();
      }
    };
    private String myDefaultName;

    MethodKind(String defaultName) {
      myDefaultName = defaultName;
    }

    public String getDefaultName() {
      return myDefaultName;
    }

    public abstract FileTemplateDescriptor getFileTemplateDescriptor(TestFramework framework);
  }

  public static boolean isTest(PsiElement element) {
    PsiClass klass = findOuterClass(element);
    return klass != null && TestUtil.isTestClass(klass);
  }

  public static PsiClass findOuterClass(PsiElement element) {
    PsiClass result = PsiTreeUtil.getParentOfType(element, PsiClass.class, false);
    if (result == null) return null;

    do {
      PsiClass nextParent = PsiTreeUtil.getParentOfType(result, PsiClass.class, true);
      if (nextParent == null) return result;
      result = nextParent;
    }
    while (true);
  }

  public static List<MemberInfo> extractClassMethods(PsiClass clazz, boolean includeInherited) {
    List<MemberInfo> result = new ArrayList<MemberInfo>();

    do {
      MemberInfo.extractClassMembers(clazz, result, new MemberInfo.Filter<PsiMember>() {
        public boolean includeMember(PsiMember member) {
          if (!(member instanceof PsiMethod)) return false;
          PsiModifierList list = member.getModifierList();
          return list.hasModifierProperty(PsiModifier.PUBLIC);
        }
      }, false);
      clazz = clazz.getSuperClass();
    }
    while (clazz != null
           && clazz.getSuperClass() != null // not the Object
           && includeInherited);

    return result;
  }

  public static void runTestMethodTemplate(MethodKind methodKind,
                                           TestFramework framework,
                                           final Editor editor,
                                           final PsiClass targetClass,
                                           final PsiMethod method,
                                           @Nullable String name,
                                           boolean automatic) {
    Template template = createTestMethodTemplate(methodKind, framework, targetClass, name, automatic);

    final TextRange range = method.getTextRange();
    editor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), "");
    editor.getCaretModel().moveToOffset(range.getStartOffset());

    final Project project = targetClass.getProject();

    TemplateEditingAdapter adapter = null;

    if (!automatic) {
      adapter = new TemplateEditingAdapter() {
        @Override
        public void templateFinished(Template template, boolean brokenOff) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
              PsiFile psi = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
              PsiElement el = PsiTreeUtil.findElementOfClassAtOffset(psi, editor.getCaretModel().getOffset() - 1, PsiMethod.class, false);

              if (el != null) {
                PsiMethod method = PsiTreeUtil.getParentOfType(el, PsiMethod.class, false);
                if (method != null) {
                  GenerateMembersUtil.setupGeneratedMethod(method);
                  CreateFromUsageUtils.setupEditor(method, editor);
                }
              }
            }
          });
        }
      };
    }

    TemplateManager.getInstance(project).startTemplate(editor, template, adapter);
    PsiDocumentManager.getInstance(targetClass.getProject()).commitDocument(editor.getDocument());
  }

  private static Template createTestMethodTemplate(MethodKind methodKind,
                                                   TestFramework descriptor,
                                                   PsiClass targetClass,
                                                   @Nullable String name,
                                                   boolean automatic) {
    FileTemplateDescriptor templateDesc = methodKind.getFileTemplateDescriptor(descriptor);
    String templateName = templateDesc.getFileName();
    FileTemplate fileTemplate = FileTemplateManager.getInstance().getCodeTemplate(templateName);
    Template template = TemplateManager.getInstance(targetClass.getProject()).createTemplate("", "");

    String templateText;
    try {
      templateText = fileTemplate.getText(new Properties());
    }
    catch (IOException e) {
      LOG.warn(e);
      templateText = fileTemplate.getText();
    }

    if (name == null) name = methodKind.getDefaultName();

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

  public static PsiMethod createDummyMethod(Project project) {
    PsiElementFactory f = JavaPsiFacade.getInstance(project).getElementFactory();
    return f.createMethod("dummy", PsiType.VOID);
  }
}
