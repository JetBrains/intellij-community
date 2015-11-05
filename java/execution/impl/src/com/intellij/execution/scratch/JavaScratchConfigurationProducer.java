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
package com.intellij.execution.scratch;

import com.intellij.execution.JavaExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.application.AbstractApplicationConfigurationProducer;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.ide.scratch.ScratchFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author Eugene Zhuravlev
 *         Date: 29-Sep-15
 */
public class JavaScratchConfigurationProducer extends AbstractApplicationConfigurationProducer<JavaScratchConfiguration> {

  public JavaScratchConfigurationProducer() {
    super(JavaScratchConfigurationType.getInstance());
  }

  @Override
  protected boolean setupConfigurationFromContext(JavaScratchConfiguration configuration, ConfigurationContext context, Ref<PsiElement> sourceElement) {
    final Location location = context.getLocation();
    if (location != null) {
      final VirtualFile vFile = location.getVirtualFile();
      if (vFile instanceof VirtualFileWithId && vFile.getFileType() == ScratchFileType.INSTANCE) {
        final PsiFile psiFile = location.getPsiElement().getContainingFile();
        if (psiFile != null && psiFile.getLanguage() == JavaLanguage.INSTANCE) {
          configuration.SCRATCH_FILE_ID = ((VirtualFileWithId)vFile).getId();
          return super.setupConfigurationFromContext(configuration, context, sourceElement);
        }
      }
    }
    return false;
  }

  @Override
  public boolean shouldReplace(ConfigurationFromContext self, ConfigurationFromContext other) {
    return other.isProducedBy(AbstractApplicationConfigurationProducer.class) && !other.isProducedBy(JavaScratchConfigurationProducer.class);
  }

  @Override
  public boolean isConfigurationFromContext(JavaScratchConfiguration configuration, ConfigurationContext context) {
    final PsiElement location = context.getPsiLocation();
    final PsiClass aClass = ApplicationConfigurationType.getMainClass(location);
    if (aClass != null && Comparing.equal(JavaExecutionUtil.getRuntimeQualifiedName(aClass), configuration.MAIN_CLASS_NAME)) {
      // for scratches it is enough to check that the configuration is associated with the same scratch file
      final VirtualFile scratchFile = configuration.getScratchVirtualFile();
      if (scratchFile != null) {
        final PsiFile containingFile = aClass.getContainingFile();
        if (containingFile != null && scratchFile.equals(containingFile.getVirtualFile())) {
          return true;
        }
      }
    }
    return false;
  }

}
