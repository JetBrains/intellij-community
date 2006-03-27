/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 11.04.2005
 * Time: 1:26:45
 */
package com.intellij.lang.properties.psi;

import com.intellij.lang.properties.ResourceBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface PropertiesFile extends PsiFile {
  @NotNull List<Property> getProperties();
  @Nullable Property findPropertyByKey(@NotNull String key);
  @NotNull List<Property> findPropertiesByKey(@NotNull String key);

  @NotNull ResourceBundle getResourceBundle();
  @NotNull Locale getLocale();

  @NotNull PsiElement addProperty(@NotNull Property property) throws IncorrectOperationException;
  @NotNull Map<String,String> getNamesMap();
}