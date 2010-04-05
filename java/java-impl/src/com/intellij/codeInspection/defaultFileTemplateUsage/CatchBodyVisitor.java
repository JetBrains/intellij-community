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
package com.intellij.codeInspection.defaultFileTemplateUsage;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.*;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.JavaTemplateUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Properties;

/**
 * @author cdr
 */
class CatchBodyVisitor extends JavaRecursiveElementWalkingVisitor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.defaultFileTemplateUsage.CatchBodyVisitor");

  Collection<ProblemDescriptor> myProblemDescriptors;
  private final boolean myOnTheFly;
  private final InspectionManager myManager;

  public CatchBodyVisitor(InspectionManager manager, Collection<ProblemDescriptor> descriptors, boolean onTheFly) {
    myManager = manager;
    myProblemDescriptors = descriptors;
    myOnTheFly = onTheFly;
  }

  @Override public void visitCatchSection(PsiCatchSection section) {
    checkSection(section);
    super.visitCatchSection(section);
  }

  @Override public void visitClass(PsiClass aClass) {
    // ignore anonymous
  }

  private void checkSection(final PsiCatchSection section) {
    final PsiParameter parameter = section.getParameter();
    if (parameter == null) return;
    PsiCodeBlock catchBlock = section.getCatchBlock();
    if (catchBlock == null) return;
    PsiType type = parameter.getType();
    if (!(type instanceof PsiClassType)) return;
    PsiCodeBlock templateCatchBlock;
    final PsiParameter templateParameter;
    try {
      final PsiJavaParserFacade elementFactory = JavaPsiFacade.getInstance(section.getProject()).getParserFacade();
      PsiCatchSection sectionTemplate = elementFactory.createCatchSection((PsiClassType)type, parameter.getName(), parameter);
      templateCatchBlock = sectionTemplate.getCatchBlock();

      // replace with default template text
      FileTemplate catchBodyTemplate = FileTemplateManager.getInstance().getDefaultTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);

      Properties props = new Properties();
      props.setProperty(FileTemplate.ATTRIBUTE_EXCEPTION, parameter.getName());
      String catchBody = catchBodyTemplate.getText(props);
      PsiCodeBlock codeBlockFromText = elementFactory.createCodeBlockFromText("{\n" + catchBody + "\n}", null);
      templateCatchBlock = (PsiCodeBlock)templateCatchBlock.replace(codeBlockFromText);

      templateParameter = sectionTemplate.getParameter();
    }
    catch (ProcessCanceledException e) {
      /// @#^$%*?
      return;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return;
    }
    catch (IOException e) {
      LOG.error(e);
      return;
    }
    // should be equal except parameter names which should resolve to corresponding parameters
    if (!PsiEquivalenceUtil.areElementsEquivalent(catchBlock, templateCatchBlock, new Comparator<PsiElement>() {
      public int compare(final PsiElement o1, final PsiElement o2) {
        if (o1 == parameter && o2 == templateParameter) return 0;
        return -1;
      }
    }, true)) {
      return;
    }
    Pair<? extends PsiElement, ? extends PsiElement> range = DefaultFileTemplateUsageInspection.getInteriorRange(catchBlock);
    final String description = InspectionsBundle.message("default.file.template.description");
    ProblemDescriptor descriptor = myManager.createProblemDescriptor(range.first, range.second, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                     myOnTheFly, createQuickFix(section));
    myProblemDescriptors.add(descriptor);
  }

  private static LocalQuickFix[] createQuickFix(final PsiCatchSection section) {
    FileTemplate template = FileTemplateManager.getInstance().getCodeTemplate(JavaTemplateUtil.TEMPLATE_CATCH_BODY);
    ReplaceWithFileTemplateFix replaceWithFileTemplateFix = new ReplaceWithFileTemplateFix() {
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        final PsiParameter parameter = section.getParameter();
        if (parameter == null) return;
        PsiCodeBlock catchBlock = section.getCatchBlock();
        if (catchBlock == null) return;
        PsiType type = parameter.getType();
        if (!(type instanceof PsiClassType)) return;
        final PsiJavaParserFacade elementFactory = JavaPsiFacade.getInstance(section.getProject()).getParserFacade();
        try {
          PsiCatchSection sectionTemplate = elementFactory.createCatchSection((PsiClassType)type, parameter.getName(), parameter);
          section.replace(sectionTemplate);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }

    };
    LocalQuickFix editFileTemplateFix = DefaultFileTemplateUsageInspection.createEditFileTemplateFix(template, replaceWithFileTemplateFix);
    if (template.isDefault()) {
      return new LocalQuickFix[]{editFileTemplateFix};
    }
    return new LocalQuickFix[]{replaceWithFileTemplateFix, editFileTemplateFix};
  }
}
