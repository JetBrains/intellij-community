package com.intellij.psi.impl.migration;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMigration;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.containers.HashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author dsl
 */
public class PsiMigrationImpl implements PsiMigration {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.migration.PsiMigrationImpl");
  private final JavaPsiFacadeImpl myFacade;
  private final PsiManagerImpl myManager;
  private final Map<String, MigrationClassImpl> myQNameToClassMap = new HashMap<String, MigrationClassImpl>();
  private final Map<String, List<PsiClass>>  myPackageToClassesMap = new HashMap<String, List<PsiClass>>();
  private final Map<String, MigrationPackageImpl> myQNameToPackageMap = new HashMap<String, MigrationPackageImpl>();
  private final Map<String, List<PsiPackage>>  myPackageToSubpackagesMap = new HashMap<String, List<PsiPackage>>();
  private boolean myIsValid = true;

  public PsiMigrationImpl(JavaPsiFacadeImpl facade, PsiManagerImpl manager) {
    myFacade = facade;
    myManager = manager;
  }

  public PsiClass createClass(String qualifiedName) {
    assertValid();
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    final MigrationClassImpl migrationClass = new MigrationClassImpl(this, qualifiedName);
    final MigrationClassImpl oldMigrationClass = myQNameToClassMap.put(qualifiedName, migrationClass);
    LOG.assertTrue(oldMigrationClass == null, qualifiedName);
    String packageName = parentPackageName(qualifiedName);
    final PsiPackage aPackage = myFacade.findPackage(packageName);
    if (aPackage == null) {
      createPackage(packageName);
    }
    List<PsiClass> psiClasses = getClassesList(packageName);
    psiClasses.add(migrationClass);
    myFacade.migrationModified(false);
    return migrationClass;
  }

  private List<PsiClass> getClassesList(String packageName) {
    assertValid();
    ApplicationManager.getApplication().assertReadAccessAllowed();
    List<PsiClass> psiClasses = myPackageToClassesMap.get(packageName);
    if (psiClasses == null) {
      psiClasses = new ArrayList<PsiClass>();
      myPackageToClassesMap.put(packageName, psiClasses);
    }
    return psiClasses;
  }

  public PsiPackage createPackage(String qualifiedName) {
    assertValid();
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    final MigrationPackageImpl migrationPackage = new MigrationPackageImpl(this, qualifiedName);
    final MigrationPackageImpl oldMigrationPackage = myQNameToPackageMap.put(qualifiedName, migrationPackage);
    LOG.assertTrue(oldMigrationPackage == null, qualifiedName);
    final String parentName = parentPackageName(qualifiedName);
    final PsiPackage aPackage = myFacade.findPackage(parentName);
    if (aPackage == null) {
      createPackage(parentName);
    }
    List<PsiPackage> psiPackages = getSubpackagesList(parentName);
    psiPackages.add(migrationPackage);
    myFacade.migrationModified(false);
    return migrationPackage;
  }

  public void finish() {
    assertValid();
    myQNameToClassMap.clear();
    myQNameToPackageMap.clear();
    myPackageToClassesMap.clear();
    myPackageToSubpackagesMap.clear();
    myIsValid = false;
    myFacade.migrationModified(true);
  }

  private void assertValid() {
    LOG.assertTrue(myIsValid);
  }

  private List<PsiPackage> getSubpackagesList(final String parentName) {
    assertValid();
    List<PsiPackage> psiPackages = myPackageToSubpackagesMap.get(parentName);
    if (psiPackages == null) {
      psiPackages = new ArrayList<PsiPackage>();
      myPackageToSubpackagesMap.put(parentName, psiPackages);
    }
    return psiPackages;
  }

  public List<PsiClass> getMigrationClasses(String packageName) {
    assertValid();
    return getClassesList(packageName);
  }

  public List<PsiPackage> getMigrationPackages(String packageName) {
    assertValid();
    return getSubpackagesList(packageName);
  }

  public PsiClass getMigrationClass(String qualifiedName) {
    assertValid();
    return myQNameToClassMap.get(qualifiedName);
  }

  public PsiPackage getMigrationPackage(String qualifiedName) {
    assertValid();
    return myQNameToPackageMap.get(qualifiedName);
  }


  private static String parentPackageName(String qualifiedName) {
    final int lastDotIndex = qualifiedName.lastIndexOf('.');
    return lastDotIndex >= 0 ? qualifiedName.substring(0, lastDotIndex) : "";
  }

  PsiManagerImpl getManager() {
    return myManager;
  }

  boolean isValid() {
    return myIsValid;
  }
}
