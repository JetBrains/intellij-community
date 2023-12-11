/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.jetbrains.lang.manifest.highlighting;

import com.intellij.codeInspection.*;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.lang.manifest.ManifestBundle;
import org.jetbrains.lang.manifest.psi.Header;
import org.jetbrains.lang.manifest.psi.ManifestFile;
import org.jetbrains.lang.manifest.psi.ManifestTokenType;
import org.jetbrains.lang.manifest.psi.Section;

import java.util.List;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public final class MissingFinalNewlineInspection extends LocalInspectionTool {
  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (file instanceof ManifestFile) {
      String text = file.getText();
      if (text != null && !text.isEmpty() && !StringUtil.endsWith(text, "\n")) {
        List<Section> sections = ((ManifestFile)file).getSections();
        assert !sections.isEmpty() : text;
        Section section = sections.get(sections.size() - 1);
        ModCommandAction action = new AddNewlineQuickFix(section);
        ProblemDescriptor descriptor = manager.createProblemDescriptor(
          section.getLastChild(), ManifestBundle.message("inspection.newline.message"),
          LocalQuickFix.from(action), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly
        );
        return new ProblemDescriptor[]{descriptor};
      }
    }

    return null;
  }

  private static final class AddNewlineQuickFix extends PsiUpdateModCommandAction<Section> {
    private AddNewlineQuickFix(Section section) {
      super(section);
    }

    @Override
    public @NotNull String getFamilyName() {
      return ManifestBundle.message("inspection.newline.fix");
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull Section section, @NotNull ModPsiUpdater updater) {
      PsiElement lastChild = section.getLastChild();
      if (lastChild instanceof Header) {
        lastChild.getNode().addLeaf(ManifestTokenType.NEWLINE, "\n", null);
      }
    }
  }
}
