package com.intellij.openapi.options;

import com.intellij.openapi.util.WriteExternalException;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public interface SchemesManager <T extends Scheme, E extends ExternalizableScheme>{
  SchemesManager EMPTY = new SchemesManager(){
    public Collection loadSchemes() {
      return Collections.emptySet();
    }

    public Collection loadScharedSchemes() {
      return Collections.emptySet();
    }

    public void exportScheme(final ExternalizableScheme scheme, final String name, final String description) throws WriteExternalException {
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

    public void setCurrentSchemeName(final String schemeName) {

    }

    public Scheme getCurrentScheme() {
      return null;
    }

    public void removeScheme(final Scheme scheme) {

    }

    public Collection getAllSchemeNames() {
      return Collections.emptySet();
    }

    public Collection loadScharedSchemes(final Collection currentSchemeList) {
      return loadScharedSchemes();
    }
  };

  Collection<E> loadSchemes();

  Collection<E> loadScharedSchemes();
  Collection<E> loadScharedSchemes(Collection<T> currentSchemeList);

  void exportScheme(final E scheme, final String name, final String description)
      throws WriteExternalException;

  boolean isImportExportAvailable();

  boolean isShared(final Scheme scheme);

  void addNewScheme(final T scheme, final boolean replaceExisting);

  void clearAllSchemes();

  List<T> getAllSchemes();

  @Nullable
  T findSchemeByName(final String schemeName);

  void save() throws WriteExternalException;

  void setCurrentSchemeName(final String schemeName);

  @Nullable
  T getCurrentScheme();

  void removeScheme(final T scheme);

  Collection<String> getAllSchemeNames();
}
