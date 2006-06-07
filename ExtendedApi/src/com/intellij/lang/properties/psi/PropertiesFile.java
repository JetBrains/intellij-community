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
import org.jetbrains.annotations.NonNls;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface PropertiesFile extends PsiFile {
  /**
   * @return All properties found in this file.
   */
  @NotNull List<Property> getProperties();

  /**
   * @param key the name of the property in the properties file
   * @return property corresponding to the key specified, or null if there is no property found.
   * If there are several properties with the same key, returns first from the top of the file property.
   */
  @Nullable Property findPropertyByKey(@NotNull @NonNls String key);

  /**
   * @param key
   * @return All properties found in this file with the name specified.
   */
  @NotNull List<Property> findPropertiesByKey(@NotNull @NonNls String key);

  @NotNull ResourceBundle getResourceBundle();
  @NotNull Locale getLocale();

  /**
   * Adds property to the end of the file.
   * @param property to add. Typically you create the property via {@link com.intellij.lang.properties.psi.PropertiesElementFactory}.
   * @return newly added property.
   * It is this value you use to do actual PSI work, e.g. call {@link com.intellij.psi.PsiElement#delete()} to remove this property from the file.
   * @throws IncorrectOperationException
   */
  @NotNull PsiElement addProperty(@NotNull Property property) throws IncorrectOperationException;

  /**
   * @return Property key to the property value map.
   * Do not modify this map. It's no use anyway.
   */
  @NotNull Map<String,String> getNamesMap();
}