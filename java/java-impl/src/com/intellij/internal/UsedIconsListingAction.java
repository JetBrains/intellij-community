/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.internal;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.util.containers.MultiMap;

import java.util.*;

public class UsedIconsListingAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = LangDataKeys.PROJECT.getData(e.getDataContext());

    final Set<String> answer = new HashSet<String>();
    final MultiMap<String, PsiCallExpression> calls = new MultiMap<String, PsiCallExpression>();


    Processor<PsiReference> consumer = new Processor<PsiReference>() {
      @Override
      public boolean process(PsiReference reference) {
        PsiCallExpression call = PsiTreeUtil.getParentOfType(reference.getElement(), PsiCallExpression.class, false);
        if (call == null) return true;
        if (call.getArgumentList() == null) return true;
        if (call.getArgumentList().getExpressions() == null) return true;

        PsiFile file = reference.getElement().getContainingFile();
        if ("AllIcons.java".equals(file.getName())) return true;
        for (PsiExpression arg : call.getArgumentList().getExpressions()) {
          if (arg instanceof PsiLiteralExpression) {
            Object value = ((PsiLiteralExpression)arg).getValue();
            processValue(value, call, file);
          }
          else {
            Object value = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper().computeConstantExpression(arg, false);
            processValue(value, call, file);
          }
        }


        return true;
      }

      private void processValue(Object value, PsiCallExpression call, PsiFile file) {
        if (value instanceof String) {
          String str = (String)value;
          if (str.startsWith("\"")) {
            str = str.substring(0);
            if (str.endsWith("\"")) {
              str = str.substring(0, str.length() - 1);
            }
          }

          if (!str.startsWith("/")) {

            if (file instanceof PsiClassOwner) {
              str = "/" + ((PsiClassOwner)file).getPackageName().replace('.', '/') + "/" + str;
            }
          }

          calls.putValue(str, call);
          answer.add(str);
        }
      }
    };

    PsiClass iconLoader =
      JavaPsiFacade.getInstance(project).findClass("com.intellij.openapi.util.IconLoader", GlobalSearchScope.allScope(project));

    PsiMethod getIconMethod = iconLoader.findMethodsByName("getIcon", false)[0];
    PsiMethod findIconMethod = iconLoader.findMethodsByName("findIcon", false)[0];
    if (false) {
      MethodReferencesSearch.search(getIconMethod, false).forEach(consumer);
      MethodReferencesSearch.search(findIconMethod, false).forEach(consumer);
    }

    PsiClass javaeeIcons = JavaPsiFacade.getInstance(project).findClass("com.intellij.javaee.oss.JavaeeIcons", GlobalSearchScope.allScope(project));
    MethodReferencesSearch.search(javaeeIcons.findMethodsByName("getIcon", false)[0], false).forEach(consumer);

    final ProjectFileIndex index = ProjectRootManager.getInstance(project).getFileIndex();
    MethodReferencesSearch.search(findIconMethod, false).forEach(consumer);

    PsiClass allIcons =
      JavaPsiFacade.getInstance(project).findClass("com.intellij.icons.AllIcons", GlobalSearchScope.allScope(project));

    final HashMap<String, String> mappings = new HashMap<String, String>();
    collectFields(allIcons, "", mappings);

    final List<XmlAttribute> victims = new ArrayList<XmlAttribute>();

    PsiSearchHelper.SERVICE.getInstance(project).processAllFilesWithWordInText(
      "icon",
      new DelegatingGlobalSearchScope(GlobalSearchScope.projectScope(project)) {
        @Override
        public boolean contains(VirtualFile file) {
          return super.contains(file) && file.getFileType() == XmlFileType.INSTANCE && index.isInSource(file);
        }
      },

      new Processor<PsiFile>() {
        @Override
        public boolean process(PsiFile file) {
          file.accept(new XmlRecursiveElementVisitor() {
            @Override
            public void visitXmlTag(XmlTag tag) {
              super.visitXmlTag(tag);

              String icon = tag.getAttributeValue("icon");
              if (icon != null) {
                answer.add(icon);
                if (mappings.containsKey(icon)) {
                  victims.add(tag.getAttribute("icon"));
                }
              }
            }
          });
          return true;
        }
      },

      true
    );

    for (final XmlAttribute victim : victims) {
      String value = victim.getValue();
      final String replacement = mappings.get(value);
      if (replacement != null) {
        new WriteCommandAction<Void>(project, victim.getContainingFile()) {
          @Override
          protected void run(Result<Void> result) throws Throwable {
            victim.setValue(replacement);
          }
        }.execute();
      }
    }

    if (true /*do replacements*/) {
      ArrayList<String> sorted = new ArrayList<String>(answer);
      Collections.sort(sorted);

      for (String icon : sorted) {
        System.out.println(icon);
      }

      final JVMElementFactory factory = JVMElementFactories.getFactory(JavaLanguage.INSTANCE, project);
      for (Map.Entry<String, Collection<PsiCallExpression>> entry : calls.entrySet()) {
        String path = entry.getKey();
        final String replacement = mappings.get(path);
        if (replacement != null) {
          for (final PsiCallExpression call : entry.getValue()) {
            new WriteCommandAction(project, call.getContainingFile()) {
              @Override
              protected void run(Result result) throws Throwable {
                JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(project);
                PsiElement expr = factory.createExpressionFromText("com.intellij.icons." + replacement, call);
                styleManager.shortenClassReferences(call.replace(expr));
              }
            }.execute();
          }
        }
      }
    }
  }

  private void collectFields(PsiClass klass, String prefix, Map<String, String> mapping) {
    String thePrefix = prefix + klass.getName() + ".";

    for (PsiClass inner : klass.getInnerClasses()) {
      collectFields(inner, thePrefix, mapping);
    }

    for (PsiField field : klass.getFields()) {
      PsiCallExpression initializer = (PsiCallExpression)field.getInitializer();
      PsiLiteralExpression arg = (PsiLiteralExpression)initializer.getArgumentList().getExpressions()[0];

      mapping.put((String)arg.getValue(), thePrefix + field.getName());
    }
  }
}

