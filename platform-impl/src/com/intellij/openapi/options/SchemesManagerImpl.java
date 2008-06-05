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

public class SchemesManagerImpl<T extends Scheme> extends AbstractSchemesManager<T> {
  private static final Logger LOG = Logger.getInstance("#" + SchemesManagerFactoryImpl.class.getName());

  private static final String EXT = ".xml";

  private final Map<String, String> mySchemeNameToFileName = new HashMap<String, String>();
  private final Map<String, Long> mySchemeNameToHashValue = new HashMap<String, Long>();
  private final Map<String, String> mySharedSchemeToOriginalPath = new HashMap<String, String>();
  private final Set<String> myDeletedNames = new LinkedHashSet<String>();
  private final Set<String> myFilesToDelete = new HashSet<String>();

  private final Map<Scheme, String> mySchemeToName = new HashMap<Scheme, String>();

  private static final String SHARED_SCHEME = "shared-scheme";
  private static final String NAME = "name";
  private static final String ORIGINAL_SCHEME_PATH = "original-scheme-path";
  private final String myFileSpec;
  private final SchemeProcessor<T> myProcessor;
  private final RoamingType myRoamingType;


  private static final String SCHEME_LOCAL_COPY = "scheme-local-copy";
  private static final String DELETED_XML = "__deleted.xml";
  private final StreamProvider[] myProviders;
  private final File myBaseDir;

  public SchemesManagerImpl(final String fileSpec, final SchemeProcessor<T> processor, final RoamingType roamingType,
                            StreamProvider[] providers, File baseDir) {

    myFileSpec = fileSpec;
    myProcessor = processor;
    myRoamingType = roamingType;
    myProviders = providers;
    myBaseDir = baseDir;
  }

  public Collection<T> loadSchemes() {

    myBaseDir.mkdirs();

    final StreamProvider[] providers = getProviders();


    myDeletedNames.addAll(readDeletedSchemeNames(providers));

    Collection<T> read = new ArrayList<T>();

    read.addAll(readSchemesFromFileSystem());


    read.addAll(readSchemesFromProviders(providers));

    initLoadedSchemes(read);

    return read;
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

  private void initLoadedSchemes(final Collection<T> read) {
    for (T scheme : read) {
      myProcessor.initScheme(scheme);
    }
  }

  private Collection<T> readSchemesFromProviders(final StreamProvider[] providers) {
    Collection<T> result = new ArrayList<T>();
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
                T scheme = readScheme(subDocument);
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
    Collection<String> schemeNames = new ArrayList<String>(mySchemeNameToFileName.keySet());
    for (String schemeName : schemeNames) {
      String name = mySchemeNameToFileName.get(schemeName);
      String fileName = name + EXT;
      if (fileName.equals(subpath)) {
        String uniqueFileName = createUniqueFileName(mySchemeNameToFileName.values(), schemeName);
        File newFile = new File(myBaseDir, uniqueFileName + EXT);
        File oldFile = new File(myBaseDir, subpath + EXT);
        if (oldFile.isFile()) {
          FileUtil.copy(oldFile, newFile);
        }
        mySchemeNameToFileName.put(schemeName,  uniqueFileName);
      }
    }
  }

  private String createUniqueFileName(final Collection<String> strings, final String schemeName) {
    return UniqueNameGenerator.generateUniqueName(schemeName, "", "", strings);
  }

  private void loadScheme(final File file, final T scheme) throws IOException {
    if (scheme != null && scheme.getName() != null && !myDeletedNames.contains(scheme.getName())) {
      if (mySchemeNameToFileName.containsKey(scheme.getName())) {
        FileUtil.delete(new File(myBaseDir, mySchemeNameToFileName.get(scheme.getName()) + EXT));
      }
      addNewScheme(scheme, true);
      saveFileName(file, scheme.getName());
      //mySchemeNameToHashValue.put(scheme.getName(), computeHashValue(subDocument));
    }
    else {
      deleteLocalAndServerFiles(file);
    }

  }

  private Collection<T> readSchemesFromFileSystem() {
    Collection<T> result = new ArrayList<T>();
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
            final T scheme = readScheme(document);
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
  private T readScheme(final Document subDocument) throws InvalidDataException, IOException, JDOMException {
    Element rootElement = subDocument.getRootElement();
    if (rootElement.getName().equals(SHARED_SCHEME)) {
      String schemeName = rootElement.getAttributeValue(NAME);
      String schemePath = rootElement.getAttributeValue(ORIGINAL_SCHEME_PATH);

      Document sharedDocument = loadGlobalScheme(schemePath);

      if (sharedDocument != null) {
        T result = readScheme(sharedDocument);
        if (result != null) {
          myProcessor.renameScheme(schemeName, result);
          mySharedSchemeToOriginalPath.put(schemeName, schemePath);
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

  private void saveFileName(final File file, final String schemeKey) {
    String fileName = file.getName();
    if (StringUtil.endsWithIgnoreCase(fileName, EXT)) {
      fileName = fileName.substring(0, fileName.length() - EXT.length());
    }
    mySchemeNameToFileName.put(schemeKey, fileName);
  }

  private static long computeHashValue(final Document document) throws IOException {
    return StringHash.calc(JDOMUtil.printDocument(document, "\n"));
  }

  @Nullable
  private Document writeSchemeToDocument(final T scheme) throws WriteExternalException {
    if (!isShared(scheme)) {
      return myProcessor.writeScheme(scheme);
    }
    else {
      String originalPath = mySharedSchemeToOriginalPath.get(scheme.getName());
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

  public Collection<T> loadScharedSchemes(Collection<String> currentSchemeNameList) {
    Collection<String> names = new HashSet<String>(currentSchemeNameList);

    final StreamProvider[] providers = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager()
        .getStreamProviders(RoamingType.GLOBAL);
    final HashMap<String, T> result = new HashMap<String, T>();
    if (providers != null) {
      for (StreamProvider provider : providers) {
        String[] paths = provider.listSubFiles(myFileSpec);
        for (String subpath : paths) {
          try {
            final Document subDocument = provider.loadDocument(getFileFullPath(subpath), RoamingType.GLOBAL);
            if (subDocument != null) {
              final T scheme = myProcessor.readScheme(subDocument);
              String schemeName = scheme.getName();
              String uniqueName = UniqueNameGenerator.generateUniqueName("[shared] " + schemeName, "", "", names);
              if (!uniqueName.equals(schemeName)) {
                renameScheme(scheme, uniqueName);
                schemeName = uniqueName;
              }
              mySharedSchemeToOriginalPath.put(schemeName, getFileFullPath(subpath));
              result.put(schemeName, scheme);
            }
          }
          catch (Exception e) {
            LOG.debug("Cannot load data from IDEAServer: " + e.getLocalizedMessage());
          }
        }
      }
    }

    for (T t : result.values()) {
      myProcessor.initScheme(t);
    }

    return result.values();

  }

  private String getFileFullPath(final String subpath) {
    return myFileSpec + "/" + subpath;
  }

  public void exportScheme(final T scheme) throws WriteExternalException {
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
    return mySharedSchemeToOriginalPath.containsKey(scheme.getName());
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
      mySchemeToName.put(scheme, scheme.getName());
      if (myProcessor.shouldBeSaved(scheme)) {
        String schemeKey = scheme.getName();
        final File file = getFileForScheme(myBaseDir, fileNameProvider, scheme);
        try {

          final Document document = writeSchemeToDocument(scheme);

          long newHash = computeHashValue(document);

          Long oldHash = mySchemeNameToHashValue.get(schemeKey);

          saveIfNeeded(providers, schemeKey, file, document, newHash, oldHash);
        }
        catch (IOException e) {
          LOG.debug(e);
          myProcessor.showWriteErrorMessage(e, scheme.getName(), file.getPath());
        }


      }


    }
  }

  private StreamProvider[] getProviders() {
    return myProviders;
  }

  private File getFileForScheme(final File baseDir, final UniqueFileNamesProvider fileNameProvider, final T scheme) {
    final String fileName;
    if (mySchemeNameToFileName.containsKey(scheme.getName())) {
      fileName = mySchemeNameToFileName.get(scheme.getName());
      fileNameProvider.reserveFileName(fileName);
    }
    else {
      fileName = fileNameProvider.suggestName(scheme.getName());
    }

    return new File(baseDir, fileName + EXT);
  }

  private void saveIfNeeded(final StreamProvider[] providers, final String schemeKey, final File file, final Document document,
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
      mySchemeNameToHashValue.put(schemeKey, newHash);
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
      String schemeKey = mySchemeToName.get(scheme);
      final String fileName = mySchemeNameToFileName.get(schemeKey);
      if (fileName != null) {
        if (schemeKey.equals(scheme.getName())) {
          fileNameProvider.reserveFileName(fileName);
        }
        else {
          myFilesToDelete.add(fileName);
          mySchemeNameToFileName.remove(schemeKey);
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
    String previouslyUsedName = mySchemeToName.get(toDelete);

    if (previouslyUsedName != null) {
      mySchemeNameToHashValue.remove(previouslyUsedName);
      mySharedSchemeToOriginalPath.remove(previouslyUsedName);
      myDeletedNames.add(previouslyUsedName);

      if (mySchemeNameToFileName.containsKey(previouslyUsedName)) {
        myFilesToDelete.add(mySchemeNameToFileName.get(previouslyUsedName));
        mySchemeNameToFileName.remove(previouslyUsedName);
      }
    }

  }

  protected void renameScheme(final T scheme, final String newName) {
    if (!newName.equals(scheme.getName())) {
      myProcessor.renameScheme(newName, scheme);
      LOG.assertTrue(newName.equals(scheme.getName()));
    }
  }

  protected void onSchemeAdded(final T scheme) {
    myDeletedNames.remove(scheme.getName());
    mySchemeToName.put(scheme, scheme.getName());    
  }
}
