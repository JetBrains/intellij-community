package org.jetbrains.lang.manifest.structure;

import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension;
import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.lang.manifest.ManifestLanguage;
import org.jetbrains.lang.manifest.psi.Header;
import org.jetbrains.lang.manifest.psi.ManifestFile;


final class ManifestStructureAwareNavbar extends StructureAwareNavBarModelExtension {

  @NotNull
  @Override
  protected Language getLanguage() {
    return ManifestLanguage.INSTANCE;
  }

  @Override
  public @Nullable String getPresentableText(Object object) {
    if (object instanceof ManifestFile manifestFile) {
      return manifestFile.getName();
    }
    if (object instanceof Header header) {
      return header.getName();
    }

    return null;
  }
}
