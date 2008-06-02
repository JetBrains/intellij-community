package com.intellij.openapi.options;

import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.Nullable;

import java.util.*;


public abstract class AbstractSchemesManager<T extends Scheme> implements SchemesManager<T> {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.options.AbstractSchemesManager");

  protected final List<T> mySchemes = new ArrayList<T>();
  private T myCurrentScheme;

  public void addNewScheme(final T scheme, final boolean replaceExisting) {
    LOG.assertTrue(scheme.getName() != null, "New scheme name should not be null");
    int toReplace = -1;
    boolean newSchemeIsShared = isShared(scheme);

    for (int i = 0; i < mySchemes.size(); i++) {
      T t = mySchemes.get(i);
      if (scheme.getName().equals(t.getName()) && newSchemeIsShared == isShared(t)) {
        toReplace = i;
        break;
      }
    }
    if (toReplace == -1) {
      mySchemes.add(scheme);
    }
    else {
      if (replaceExisting) {
        mySchemes.set(toReplace, scheme);
      }
      else {
        renameScheme(scheme, generateUniqueName(scheme));
        mySchemes.add(scheme);
      }
    }
    onSchemeAdded(scheme);
  }

  private String generateUniqueName(final T scheme) {
    return UniqueNameGenerator.generateUniqueName(scheme.getName(), "", "", collectExistingNames());
  }

  private Collection<String> collectExistingNames() {
    HashSet<String> result = new HashSet<String>();
    for (T scheme : mySchemes) {
      result.add(scheme.getName());
    }
    return result;
  }

  public void clearAllSchemes() {
    for (T t : getAllSchemes()) {
      removeScheme(t);
    }
  }

  public List<T> getAllSchemes() {
    return Collections.unmodifiableList(new ArrayList<T>(mySchemes));
  }

  @Nullable
  public T findSchemeByName(final String schemeName) {
    for (T scheme : mySchemes) {
      if (scheme.getName().equals(schemeName)) {
        return scheme;
      }
    }

    return null;
  }

  public abstract void save() throws WriteExternalException;

  public void setCurrentScheme(final T scheme) {
    if (scheme != null) {
      addNewScheme(scheme, true);
    }
    myCurrentScheme = scheme;
  }

  public T getCurrentScheme() {
    return myCurrentScheme;
  }

  public void removeScheme(final T scheme) {
    String schemeName = scheme.getName();
    Scheme toDelete = findSchemeToDelete(schemeName);

    mySchemes.remove(toDelete);

    onSchemeDeleted(toDelete);
  }

  protected abstract void onSchemeDeleted(final Scheme toDelete);

  private Scheme findSchemeToDelete(final String schemeName) {
    for (T scheme : mySchemes) {
      if (schemeName.equals(scheme.getName())) return scheme;
    }

    return null;
  }


  public Collection<String> getAllSchemeNames() {
    return getAllSchemeNames(mySchemes);
  }

  public Collection<String> getAllSchemeNames(Collection<T> schemes) {
    Set<String> names = new HashSet<String>();
    for (T scheme : schemes) {
      names.add(scheme.getName());
    }
    return names;
  }

  protected abstract void onSchemeAdded(final T scheme);

  protected abstract void renameScheme(final T scheme, String newName);

  public Collection<T> loadScharedSchemes() {
    return loadScharedSchemes(getAllSchemeNames());
  }
}
