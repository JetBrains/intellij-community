package com.intellij.openapi.options;

import com.intellij.openapi.util.WriteExternalException;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public interface SchemesManager <T extends Scheme>{
  SchemesManager EMPTY = new SchemesManager(){
    public Collection loadSchemes() {
      return Collections.emptySet();
    }

    public Collection loadScharedSchemes() {
      return Collections.emptySet();
    }

    public void exportScheme(final Scheme scheme) throws WriteExternalException {
    }

    public boolean isImportExportAvailable() {
      return false;
    }

    public boolean isShared(final Scheme scheme) {
      return false;
    }

    public void addNewScheme(final Scheme scheme, final boolean replaceExisting) {

    }

    public void clearAllSchemes() {
    }

    public List getAllSchemes() {
      return Collections.emptyList();
    }

    public Scheme findSchemeByName(final String schemeName) {
      return null;
    }

    public void save() {
    }

    public void setCurrentScheme(final Scheme scheme) {

    }

    public Scheme getCurrentScheme() {
      return null;
    }

    public void removeScheme(final Scheme scheme) {

    }

    public Collection getAllSchemeNames() {
      return Collections.emptySet();
    }

    public Collection loadScharedSchemes(final Collection currentSchemeNameList) {
      return loadScharedSchemes();
    }
  };

  Collection<T> loadSchemes();

  Collection<T> loadScharedSchemes();
  Collection<T> loadScharedSchemes(Collection<String> currentSchemeNameList);

  void exportScheme(final T scheme)
      throws WriteExternalException;

  boolean isImportExportAvailable();

  boolean isShared(final Scheme scheme);

  void addNewScheme(final T scheme, final boolean replaceExisting);

  void clearAllSchemes();

  List<T> getAllSchemes();

  @Nullable
  T findSchemeByName(final String schemeName);

  void save() throws WriteExternalException;

  void setCurrentScheme(final T scheme);

  T getCurrentScheme();

  void removeScheme(final T scheme);

  Collection<String> getAllSchemeNames();
}
