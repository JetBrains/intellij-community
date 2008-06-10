package com.intellij.openapi.options;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.UniqueFileNamesProvider;
import com.intellij.util.containers.HashSet;
import com.intellij.util.text.UniqueNameGenerator;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SchemesManagerImpl<T extends Scheme,E extends ExternalizableScheme> extends AbstractSchemesManager<T,E> {
  private static final Logger LOG = Logger.getInstance("#" + SchemesManagerFactoryImpl.class.getName());

  private static final String EXT = ".xml";

  private final Set<String> myDeletedNames = new LinkedHashSet<String>();
  private final Set<String> myFilesToDelete = new HashSet<String>();

  private static final String SHARED_SCHEME = "shared-scheme";
  private static final String NAME = "name";
  private static final String ORIGINAL_SCHEME_PATH = "original-scheme-path";
  private final String myFileSpec;
  private final SchemeProcessor<E> myProcessor;
  private final RoamingType myRoamingType;


  private static final String SCHEME_LOCAL_COPY = "scheme-local-copy";
  private static final String DELETED_XML = "__deleted.xml";
  private final StreamProvider[] myProviders;
  private final File myBaseDir;

  public SchemesManagerImpl(final String fileSpec, final SchemeProcessor<E> processor, final RoamingType roamingType,
                            StreamProvider[] providers, File baseDir) {

    myFileSpec = fileSpec;
    myProcessor = processor;
    myRoamingType = roamingType;
    myProviders = providers;
    myBaseDir = baseDir;
  }

  public Collection<E> loadSchemes() {

    myBaseDir.mkdirs();

    final StreamProvider[] providers = getProviders();


    myDeletedNames.addAll(readDeletedSchemeNames(providers));


    Map<String, E> read = new LinkedHashMap<String, E>();

    for (E e : readSchemesFromFileSystem()) {
      read.put(e.getName(), e);
    }

    for (E e : readSchemesFromProviders(providers)) {
      read.put(e.getName(), e);
    }

    Collection<E> result = read.values();
    initLoadedSchemes(result);

    return result;
  }

  private Collection<String> readDeletedSchemeNames(final StreamProvider[] providers) {
    Collection<String> result = new HashSet<String>();
    for (StreamProvider provider : providers) {
      try {
        Document deletedNameDoc = provider.loadDocument(getFileFullPath(DELETED_XML), myRoamingType);
        if (deletedNameDoc != null) {
          for (Object child : deletedNameDoc.getRootElement().getChildren()) {
            String deletedSchemeName = ((Element)child).getAttributeValue("name");
            if (deletedSchemeName != null) {
              result.add(deletedSchemeName);
            }
          }
        }
      }
      catch (Exception e) {
        LOG.debug(e);
      }
    }

    return result;

  }

  private void initLoadedSchemes(final Collection<E> read) {
    for (E scheme : read) {
      myProcessor.initScheme(scheme);
      checkCurrentScheme(scheme);
    }
  }

  private Collection<E> readSchemesFromProviders(final StreamProvider[] providers) {
    Collection<E> result = new ArrayList<E>();
    if (providers != null) {
      for (StreamProvider provider : providers) {
        String[] paths = provider.listSubFiles(myFileSpec);
        for (String subpath : paths) {
          if (!subpath.equals(DELETED_XML)) {
            try {
              final Document subDocument = provider.loadDocument(getFileFullPath(subpath), myRoamingType);
              if (subDocument != null) {
                checkFileNameIsFree(subpath);
                final File file = new File(myBaseDir, subpath);
                JDOMUtil.writeDocument(subDocument, file, "\n");
                E scheme = readScheme(subDocument);
                loadScheme(file, scheme);
                result.add(scheme);

              }
            }
            catch (Exception e) {
              LOG.info("Cannot load data from IDEAServer: " + e.getLocalizedMessage());
            }
          }
        }
      }
    }
    return result;
  }

  private void checkFileNameIsFree(final String subpath) throws IOException {
    for (Scheme scheme : mySchemes) {
      if (scheme instanceof ExternalizableScheme) {
        ExternalInfo externalInfo = ((ExternalizableScheme)scheme).getExternalInfo();
        String name = externalInfo.getCurrentFileName();
        if (name != null) {
          String fileName = name + EXT;
          if (fileName.equals(subpath)) {
            String uniqueFileName = createUniqueFileName(collectAllFileNames(), scheme.getName());
            File newFile = new File(myBaseDir, uniqueFileName + EXT);
            File oldFile = new File(myBaseDir, subpath + EXT);
            if (oldFile.isFile()) {
              FileUtil.copy(oldFile, newFile);
            }
            externalInfo.setCurrentFileName(uniqueFileName);
          }
        }
      }
      }
  }

  private Collection<String> collectAllFileNames() {
    HashSet<String> result = new HashSet<String>();
    for (T scheme : mySchemes) {
      if (scheme instanceof ExternalizableScheme) {
        ExternalInfo externalInfo = ((ExternalizableScheme)scheme).getExternalInfo();
        if (externalInfo.getCurrentFileName() != null) {
          result.add(externalInfo.getCurrentFileName());
        }
      }
    }
    return result;
  }

  private String createUniqueFileName(final Collection<String> strings, final String schemeName) {
    return UniqueNameGenerator.generateUniqueName(schemeName, "", "", strings);
  }

  private void loadScheme(final File file, final E scheme) throws IOException {
    if (scheme != null && scheme.getName() != null && !myDeletedNames.contains(scheme.getName())) {
      T existing = findSchemeByName(scheme.getName());
      if (existing != null && existing instanceof ExternalizableScheme) {
        ExternalInfo info = ((ExternalizableScheme)existing).getExternalInfo();
        if (info.getCurrentFileName() != null) {
          FileUtil.delete(new File(myBaseDir, info.getCurrentFileName() + EXT));
        }
      }
      addNewScheme((T)scheme, true);
      saveFileName(file, scheme);
      scheme.getExternalInfo().setPreviouslySavedName(scheme.getName());
    }
    else {
      deleteLocalAndServerFiles(file);
    }

  }

  private Collection<E> readSchemesFromFileSystem() {
    Collection<E> result = new ArrayList<E>();
    final File[] files = myBaseDir.listFiles();
    if (files != null) {
      for (File file : files) {
        final String name = file.getName();
        if (file.isFile() && StringUtil.endsWithIgnoreCase(name, EXT)) {
          try {
            final Document document;
            try {
              document = JDOMUtil.loadDocument(file);
            }
            catch (JDOMException e) {
              LOG.info("Error reading file " + file.getPath() + ": " + e.getLocalizedMessage());
              throw e;
            }
            final E scheme = readScheme(document);
            if (scheme != null) {
              loadScheme(file, scheme);
              result.add(scheme);
            }
          }
          catch (Exception e) {
            myProcessor.showReadErrorMessage(e, name, file.getPath());
          }
        }
      }
    }
    else {
      LOG.error("Cannot read directory: " + myBaseDir.getAbsolutePath());
    }
    return result;
  }

  @Nullable
  private E readScheme(final Document subDocument) throws InvalidDataException, IOException, JDOMException {
    Element rootElement = subDocument.getRootElement();
    if (rootElement.getName().equals(SHARED_SCHEME)) {
      String schemeName = rootElement.getAttributeValue(NAME);
      String schemePath = rootElement.getAttributeValue(ORIGINAL_SCHEME_PATH);

      Document sharedDocument = loadGlobalScheme(schemePath);

      if (sharedDocument != null) {
        E result = readScheme(sharedDocument);
        if (result != null) {
          renameScheme(result, schemeName);
          result.getExternalInfo().setOriginalPath(schemePath);
          result.getExternalInfo().setIsImported(true);
        }
        return result;
      }
      else {
        Element localCopyElement = subDocument.getRootElement().getChild(SCHEME_LOCAL_COPY);
        if (localCopyElement != null) {
          Element firstChild = (Element)localCopyElement.getChildren().get(0);
          return myProcessor.readScheme(new Document((Element)firstChild.clone()));
        }
        else {
          return null;
        }
      }
    }
    else {
      return myProcessor.readScheme(subDocument);
    }

  }

  @Nullable
  private static Document loadGlobalScheme(final String schemePath) throws IOException, JDOMException {
    final StreamProvider[] providers = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager()
        .getStreamProviders(RoamingType.GLOBAL);
    for (StreamProvider provider : providers) {
      Document document = provider.loadDocument(schemePath, RoamingType.GLOBAL);
      if (document != null) return document;
    }

    return null;
  }

  private void saveFileName(final File file, final E schemeKey) {
    String fileName = file.getName();
    if (StringUtil.endsWithIgnoreCase(fileName, EXT)) {
      fileName = fileName.substring(0, fileName.length() - EXT.length());
    }
    schemeKey.getExternalInfo().setCurrentFileName(fileName);
  }

  private static long computeHashValue(final Document document) throws IOException {
    return StringHash.calc(JDOMUtil.printDocument(document, "\n"));
  }

  @Nullable
  private Document writeSchemeToDocument(final E scheme) throws WriteExternalException {
    if (!isShared(scheme)) {
      return myProcessor.writeScheme(scheme);
    }
    else {
      String originalPath = scheme.getExternalInfo().getOriginalPath();
      if (originalPath != null) {
        Element root = new Element(SHARED_SCHEME);
        root.setAttribute(NAME, scheme.getName());
        root.setAttribute(ORIGINAL_SCHEME_PATH, originalPath);

        Element localCopy = new Element(SCHEME_LOCAL_COPY);
        localCopy.addContent((Element)myProcessor.writeScheme(scheme).getRootElement().clone());

        root.addContent(localCopy);

        return new Document(root);
      }
      else {
        return null;
      }
    }
  }

  public Collection<E> loadScharedSchemes(Collection<String> currentSchemeNameList) {
    Collection<String> names = new HashSet<String>(currentSchemeNameList);

    final StreamProvider[] providers = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager()
        .getStreamProviders(RoamingType.GLOBAL);
    final HashMap<String, E> result = new HashMap<String, E>();
    if (providers != null) {
      for (StreamProvider provider : providers) {
        String[] paths = provider.listSubFiles(myFileSpec);
        for (String subpath : paths) {
          try {
            final Document subDocument = provider.loadDocument(getFileFullPath(subpath), RoamingType.GLOBAL);
            if (subDocument != null) {
              final E scheme = myProcessor.readScheme(subDocument);
              String schemeName = scheme.getName();
              String uniqueName = UniqueNameGenerator.generateUniqueName("[shared] " + schemeName, "", "", names);
              if (!uniqueName.equals(schemeName)) {
                renameScheme(scheme, uniqueName);
                schemeName = uniqueName;
              }
              scheme.getExternalInfo().setOriginalPath(getFileFullPath(subpath));
              scheme.getExternalInfo().setIsImported(true);
              result.put(schemeName, scheme);
            }
          }
          catch (Exception e) {
            LOG.debug("Cannot load data from IDEAServer: " + e.getLocalizedMessage());
          }
        }
      }
    }

    for (E t : result.values()) {
      myProcessor.initScheme(t);
    }

    return result.values();

  }

  private String getFileFullPath(final String subpath) {
    return myFileSpec + "/" + subpath;
  }

  public void exportScheme(final E scheme) throws WriteExternalException {
    final StreamProvider[] providers = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager()
        .getStreamProviders(RoamingType.GLOBAL);
    if (providers != null) {
      Document document = myProcessor.writeScheme(scheme);
      for (StreamProvider provider : providers) {
        try {
          provider.saveContent(getFileFullPath(UniqueFileNamesProvider.convertName(scheme.getName())) + EXT, document, RoamingType.GLOBAL);
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }
    }

  }

  public boolean isImportExportAvailable() {
    final StreamProvider[] providers = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager()
        .getStreamProviders(RoamingType.GLOBAL);

    return providers != null && providers.length > 0;
  }

  public boolean isShared(final Scheme scheme) {
    return scheme instanceof ExternalizableScheme && ((ExternalizableScheme)scheme).getExternalInfo().isIsImported();
  }

  public void save() throws WriteExternalException {
    Collection<T> schemes = getAllSchemes();
    myBaseDir.mkdirs();


    final StreamProvider[] providers = getProviders();

    UniqueFileNamesProvider fileNameProvider = new UniqueFileNamesProvider();

    reserveUsingFileNames(schemes, fileNameProvider);

    deleteFilesFromDeletedSchemes();
    saveSchemes(schemes, providers, fileNameProvider);

    if (myDeletedNames.size() > 0) {
      for (StreamProvider provider : providers) {
        try {
          provider.saveContent(getFileFullPath(DELETED_XML), createDeletedDocument(), myRoamingType);
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }
    }
    else {
      for (StreamProvider provider : providers) {
        provider.deleteFile(getFileFullPath(DELETED_XML), myRoamingType);
      }

    }
  }

  private void deleteFilesFromDeletedSchemes() {
    for (String deletedName : myFilesToDelete) {
      deleteLocalAndServerFiles(new File(myBaseDir, deletedName + EXT));
    }
    myFilesToDelete.clear();
  }

  private void deleteLocalAndServerFiles(final File file) {
    FileUtil.delete(file);
    for (StreamProvider provider : getProviders()) {
      provider.deleteFile(getFileFullPath(file.getName()), myRoamingType);
    }
  }

  private void saveSchemes(final Collection<T> schemes, final StreamProvider[] providers, final UniqueFileNamesProvider fileNameProvider)
      throws WriteExternalException {
    for (T scheme : schemes) {
      if (isExternalizable(scheme)) {
        E eScheme = (E)scheme;
        eScheme.getExternalInfo().setPreviouslySavedName(eScheme.getName());
        if (myProcessor.shouldBeSaved(eScheme)) {
          final File file = getFileForScheme(myBaseDir, fileNameProvider, eScheme);
          try {

            final Document document = writeSchemeToDocument(eScheme);

            long newHash = computeHashValue(document);

            Long oldHash = eScheme.getExternalInfo().getHash();

            saveIfNeeded(providers, eScheme, file, document, newHash, oldHash);
          }
          catch (IOException e) {
            LOG.debug(e);
            myProcessor.showWriteErrorMessage(e, scheme.getName(), file.getPath());
          }
        }


      }


    }
  }

  private StreamProvider[] getProviders() {
    return myProviders;
  }

  private File getFileForScheme(final File baseDir, final UniqueFileNamesProvider fileNameProvider, final E scheme) {
    final String fileName;
    if (scheme.getExternalInfo().getCurrentFileName() != null) {
      fileName = scheme.getExternalInfo().getCurrentFileName();
      fileNameProvider.reserveFileName(fileName);
    }
    else {
      fileName = fileNameProvider.suggestName(scheme.getName());
    }

    return new File(baseDir, fileName + EXT);
  }

  private void saveIfNeeded(final StreamProvider[] providers, final E schemeKey, final File file, final Document document,
                            final long newHash,
                            final Long oldHash) throws IOException {
    if (oldHash == null || newHash != oldHash.longValue() || !file.isFile()) {
      if (oldHash != null && newHash != oldHash.longValue()) {
        final byte[] text = StorageUtil.printDocument(document);

        if (!Arrays.equals(FileUtil.loadFileBytes(file), text)) {
          FileUtil.writeToFile(file, text);        
        }

      }
      else {
        JDOMUtil.writeDocument(document, file, "\n");
      }
      schemeKey.getExternalInfo().setHash(newHash);
      saveFileName(file, schemeKey);

      saveOnServer(providers, file, document);
    }
  }

  private void saveOnServer(final StreamProvider[] providers, final File file, final Document document) {
    if (providers != null) {
      for (StreamProvider provider : providers) {
        try {
          provider.saveContent(getFileFullPath(file.getName()), document, myRoamingType);
        }
        catch (IOException e) {
          LOG.debug(e);
        }
      }
    }
  }

  private void reserveUsingFileNames(final Collection<T> schemes, final UniqueFileNamesProvider fileNameProvider) {
    fileNameProvider.reserveFileName(DELETED_XML);

    for (T scheme : schemes) {
      if (scheme instanceof ExternalizableScheme) {
        ExternalInfo info = ((ExternalizableScheme)scheme).getExternalInfo();
        final String fileName = info.getCurrentFileName();
        if (fileName != null) {
          if (info.getPreviouslySavedName().equals(scheme.getName())) {
            fileNameProvider.reserveFileName(fileName);
          }
          else {
            myFilesToDelete.add(fileName);
            info.setCurrentFileName(null);
          }
        }

      }
    }
  }

  private Document createDeletedDocument() {
    Element root = new Element("deleted-schemes");
    Document result = new Document(root);
    for (String deletedName : myDeletedNames) {
      Element child = new Element("scheme");
      root.addContent(child);
      child.setAttribute("name", deletedName);
    }

    return result;
  }

  protected void onSchemeDeleted(final Scheme toDelete) {
    if (toDelete instanceof ExternalizableScheme) {
      ExternalInfo info = ((ExternalizableScheme)toDelete).getExternalInfo();
      String previouslyUsedName = info.getPreviouslySavedName();

      if (previouslyUsedName != null) {
        myDeletedNames.add(previouslyUsedName);

        if (info.getCurrentFileName() != null) {
          myFilesToDelete.add(info.getCurrentFileName());
        }
      }

    }

  }

  protected void onSchemeAdded(final T scheme) {
    myDeletedNames.remove(scheme.getName());
    if (scheme instanceof ExternalizableScheme) {
      ((ExternalizableScheme)scheme).getExternalInfo().setPreviouslySavedName(scheme.getName());
    }
  }
}
