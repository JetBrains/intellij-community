// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.copyright;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.psi.UpdateCopyright;
import com.maddyhome.idea.copyright.psi.UpdateCopyrightsProvider;
import com.maddyhome.idea.copyright.psi.UpdateXmlCopyrightsProvider;
import org.intellij.images.fileTypes.impl.SvgFileType;

/**
 * @author Konstantin Bulenkov
 */
public class SvgCopyrightUpdater extends UpdateCopyrightsProvider {
  @Override
  public UpdateCopyright createInstance(Project project,
                                        Module module,
                                        VirtualFile file,
                                        FileType base,
                                        CopyrightProfile options) {
    return new UpdateSvgFileCopyright(project, module, file, options);
  }

  public static class UpdateSvgFileCopyright extends UpdateXmlCopyrightsProvider.UpdateXmlFileCopyright {
    UpdateSvgFileCopyright(final Project project, final Module module, final VirtualFile file, final CopyrightProfile options) {
      super(project, module, file, options);
    }

    @Override
    protected boolean accept() {
      return getFile().getFileType() == SvgFileType.INSTANCE;
    }
  }
}
