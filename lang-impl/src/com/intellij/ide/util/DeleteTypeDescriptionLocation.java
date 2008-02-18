package com.intellij.ide.util;

import com.intellij.ide.IdeBundle;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class DeleteTypeDescriptionLocation implements ElementDescriptionLocation {
  private boolean myPlural;

  private DeleteTypeDescriptionLocation(final boolean plural) {
    myPlural = plural;
  }

  public static final DeleteTypeDescriptionLocation SINGULAR = new DeleteTypeDescriptionLocation(false);
  public static final DeleteTypeDescriptionLocation PLURAL = new DeleteTypeDescriptionLocation(true);
                                                  
  private static final ElementDescriptionProvider ourDefaultProvider = new DefaultProvider();

  public ElementDescriptionProvider getDefaultProvider() {
    return ourDefaultProvider;
  }

  public boolean isPlural() {
    return myPlural;
  }

  public static class DefaultProvider implements ElementDescriptionProvider {
    public String getElementDescription(final PsiElement element, @Nullable final ElementDescriptionLocation location) {
      if (location instanceof DeleteTypeDescriptionLocation) {
        final boolean plural = ((DeleteTypeDescriptionLocation)location).isPlural();
        int count = plural ? 2 : 1;
        if (element instanceof PsiFile) {
          return IdeBundle.message("prompt.delete.file", count);
        }
        if (element instanceof PsiDirectory) {
          return IdeBundle.message("prompt.delete.directory", count);
        }
        if (!plural) {
          return LanguageFindUsages.INSTANCE.forLanguage(element.getLanguage()).getType(element);
        }
        return "elements";
      }
      return null;
    }
  }
}
