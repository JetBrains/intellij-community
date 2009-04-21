package com.intellij.lang.jsp;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.psi.PsiFile;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: 20.04.2009
 * Time: 17:07:57
 * To change this template use File | Settings | File Templates.
 */
public interface IBaseJspManager {
  XmlNSDescriptor getActionsLibrary(@NotNull PsiFile containingFile);

  @Nullable
  XmlElementDescriptor getDirectiveDescriptorByName(String name, @NotNull PsiFile context);
}
