package com.intellij.openapi.options;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.impl.stores.StorageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
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
  private static final String SHARED_SCHEME_ORIGINAL = "shared-scheme-original";
  private static final String NAME = "name";
  private static final String ORIGINAL_SCHEME_PATH = "original-scheme-path";
  private final String myFileSpec;
  private final SchemeProcessor<E> myProcessor;
  private final RoamingType myRoamingType;


  private static final String SCHEME_LOCAL_COPY = "scheme-local-copy";
  private static final String DELETED_XML = "__deleted.xml";
  private final StreamProvider[] myProviders;
  private final File myBaseDir;
  private static final String DESCRIPTION = "description";
  private static final boolean EXPORT_IS_AVAILABLE = true;
  private static final String USER = "user";

  private boolean myIsUpdating = false;

  private boolean myListenerAdded = false;

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

    //addVFSListener();
    try {
      myIsUpdating = true;
      myDeletedNames.addAll(readDeletedSchemeNames());


      Map<String, E> read = new LinkedHashMap<String, E>();

      for (E e : readSchemesFromFileSystem()) {
        read.put(e.getName(), e);
      }

      for (E e : readSchemesFromProviders()) {
        read.put(e.getName(), e);
      }

      Collection<E> result = read.values();
      initLoadedSchemes(result);

      return result;

    }
    finally {
      myIsUpdating = false;
    }


  }

  private void addVFSListener() {
    if (ApplicationManager.getApplication() == null || myListenerAdded) return;

    LocalFileSystem system = LocalFileSystem.getInstance();
    final VirtualFile baseDirFile = new WriteAction<VirtualFile>() {
      protected void run(final Result<VirtualFile> result) {
        result.setResult(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myBaseDir));
      }
    }.execute().getResultObject();


    baseDirFile.getChildren();

    system.addVirtualFileListener(new VirtualFileAdapter(){
      @Override
      public void contentsChanged(final VirtualFileEvent event) {
        onFileContentChanged(event, baseDirFile);
      }

      @Override
      public void fileCreated(final VirtualFileEvent event) {
        VirtualFile file = event.getFile();

        if (!myIsUpdating && event.getRequestor() == null && isFileUnder(file, baseDirFile)) {
          File ioFile = new File(file.getPath());
          ArrayList<E> read = new ArrayList<E>();
          readSchemeFromFile(read, ioFile, true);
          if (!read.isEmpty()) {
            E readScheme = read.get(0);
            myProcessor.initScheme(readScheme);
            myProcessor.onSchemeAdded(readScheme);
          }
        }

      }

      @Override
      public void fileDeleted(final VirtualFileEvent event) {
        VirtualFile parent = event.getParent();

        if (!myIsUpdating && event.getRequestor() == null && parent != null && parent.equals(baseDirFile)) {
          File ioFile = new File(event.getFileName());
          E scheme = findSchemeFor(ioFile.getName());
          T oldCurrentScheme = null;
          if (scheme != null) {
            oldCurrentScheme = getCurrentScheme();
            removeScheme((T)scheme);
            myProcessor.onSchemeDeleted(scheme);
          }

          T newCurrentScheme = getCurrentScheme();

          if (oldCurrentScheme != null && newCurrentScheme == null) {
            if (!mySchemes.isEmpty()) {
              setCurrentSchemeName(mySchemes.get(0).getName());
              newCurrentScheme = getCurrentScheme();
            }
          }

          if (oldCurrentScheme != newCurrentScheme) {
            myProcessor.onCurrentSchemeChanged(oldCurrentScheme);
          }

        }

      }

    });

    myListenerAdded = true;
  }

  private void onFileContentChanged(final VirtualFileEvent event, final VirtualFile baseDirFile) {
    VirtualFile file = event.getFile();

    if (!myIsUpdating && event.getRequestor() == null && isFileUnder(file, baseDirFile)) {
      File ioFile = new File(file.getPath());
      E scheme = findSchemeFor(ioFile.getName());
      ArrayList<E> read = new ArrayList<E>();
      T oldCurrentScheme = null;
      if (scheme != null) {
        oldCurrentScheme = getCurrentScheme();
        removeScheme((T)scheme);
        myProcessor.onSchemeDeleted(scheme);
      }

      readSchemeFromFile(read, ioFile, true);
      if (!read.isEmpty()) {
        E readScheme = read.get(0);
        myProcessor.initScheme(readScheme);

        myProcessor.onSchemeAdded(readScheme);

        T newCurrentScheme = getCurrentScheme();

        if (oldCurrentScheme != null && newCurrentScheme == null) {
          setCurrentSchemeName(readScheme.getName());
          newCurrentScheme = getCurrentScheme();
        }

        if (oldCurrentScheme != newCurrentScheme) {
          myProcessor.onCurrentSchemeChanged(oldCurrentScheme);
        }
      }
    }
  }

  private E findSchemeFor(final String ioFileName) {
    for (T scheme : mySchemes) {
      if (scheme instanceof ExternalizableScheme) {
        String fileName = ((ExternalizableScheme)scheme).getExternalInfo().getCurrentFileName();
        if(ioFileName.equals(fileName + EXT)) {
          return (E)scheme;
        }
      }
    }
    return null;
  }

  private boolean isFileUnder(final VirtualFile file, final VirtualFile baseDirFile) {
    return file.getParent().equals(baseDirFile);
  }

  private Collection<String> readDeletedSchemeNames() {
    Collection<String> result = new HashSet<String>();
    for (StreamProvider provider : getEnabledProviders()) {
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

  private Collection<E> readSchemesFromProviders() {
    Collection<E> result = new ArrayList<E>();
    for (StreamProvider provider : getEnabledProviders()) {
      String[] paths = provider.listSubFiles(myFileSpec);
      for (String subpath : paths) {
        if (!subpath.equals(DELETED_XML)) {
          try {
            final Document subDocument = provider.loadDocument(getFileFullPath(subpath), myRoamingType);
            if (subDocument != null) {
              checkFileNameIsFree(subpath);
              final File file = new File(myBaseDir, subpath);
              byte[] text = JDOMUtil.writeDocument(subDocument, "\n").getBytes();
              if (needsSave(file, text)) {
                FileUtil.writeToFile(file, text);
              }
              E scheme = readScheme(subDocument);
              if (scheme != null) {
                loadScheme(file, scheme, false);
                result.add(scheme);
              }

            }
          }
          catch (Exception e) {
            LOG.info("Cannot load data from IDEAServer: " + e.getLocalizedMessage());
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
            String uniqueFileName = createUniqueFileName(collectAllFileNames(), UniqueFileNamesProvider.convertName(scheme.getName()));
            File newFile = new File(myBaseDir, uniqueFileName + EXT);
            File oldFile = new File(myBaseDir, subpath);
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

  private void loadScheme(final File file, final E scheme, boolean forceAdd) throws IOException {
    if (scheme != null && scheme.getName() != null && (!myDeletedNames.contains(scheme.getName()) || forceAdd)) {
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
        readSchemeFromFile(result, file, false);
      }
    }
    else {
      ApplicationManager.getApplication().invokeLater(
          new Runnable(){
            public void run() {
              String msg = "Cannot read directory: " + myBaseDir.getAbsolutePath() + " directory does not exist";
              Messages.showErrorDialog(msg, "Read Settings");
            }
          }
      );
    }
    return result;
  }

  private void readSchemeFromFile(final Collection<E> result, final File file, final boolean forceAdd) {
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
          loadScheme(file, scheme, forceAdd);
          result.add(scheme);
        }
      }
      catch (final Exception e) {
        ApplicationManager.getApplication().invokeLater(
            new Runnable(){
              public void run() {
                String msg = "Cannot read scheme " + file.getName() + "  from '" + myFileSpec + "': " + e.getLocalizedMessage();
                LOG.warn(msg, e);
                Messages.showErrorDialog(msg, "Load Settings");
              }
            }
        );

      }
    }
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
    else if (rootElement.getName().equals(SHARED_SCHEME_ORIGINAL)) {
      SchemesManagerImpl<T, E>.SharedSchemeData schemeData = unwrap(subDocument);
      E scheme = myProcessor.readScheme(schemeData.original);
      if (scheme != null) {
        renameScheme(scheme, schemeData.name);
      }
      return scheme;
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

  class SharedSchemeData {
    Document original;
    String name;
    String user;
    String description;
    E scheme;
  }

  public Collection<SharedScheme<E>> loadScharedSchemes(Collection<T> currentSchemeList) {
    Collection<String> names = new HashSet<String>(getAllSchemeNames(currentSchemeList));

    final StreamProvider[] providers = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager()
        .getStreamProviders(RoamingType.GLOBAL);
    final HashMap<String, SharedScheme<E>> result = new HashMap<String, SharedScheme<E>>();
    if (providers != null) {
      for (StreamProvider provider : providers) {
        if (provider.isEnabled()) {
          String[] paths = provider.listSubFiles(myFileSpec);
          for (String subpath : paths) {
            try {
              final Document subDocument = provider.loadDocument(getFileFullPath(subpath), RoamingType.GLOBAL);
              if (subDocument != null) {
                SharedSchemeData original = unwrap(subDocument);
                final E scheme = myProcessor.readScheme(original.original);
                if (!alreadyShared(subpath, currentSchemeList)) {
                  String schemeName = original.name;
                  String uniqueName = UniqueNameGenerator.generateUniqueName("[shared] " + schemeName, "", "", names);
                  renameScheme(scheme, uniqueName);
                  schemeName = uniqueName;
                  scheme.getExternalInfo().setOriginalPath(getFileFullPath(subpath));
                  scheme.getExternalInfo().setIsImported(true);
                  result.put(schemeName, new SharedScheme<E>(original.user == null ? "unknown" : original.user, original.description, scheme));
                }
              }
            }
            catch (Exception e) {
              LOG.debug("Cannot load data from IDEAServer: " + e.getLocalizedMessage());
            }
          }
        }
      }
    }

    for (SharedScheme<E> t : result.values()) {
      myProcessor.initScheme(t.getScheme());
    }

    return result.values();

  }

  private SharedSchemeData unwrap(final Document subDocument) {
    SharedSchemeData result = new SharedSchemeData();
    Element rootElement = subDocument.getRootElement();
    if (rootElement.getName().equals(SHARED_SCHEME_ORIGINAL)) {
      result.name = rootElement.getAttributeValue(NAME);
      result.description = rootElement.getAttributeValue(DESCRIPTION);
      result.user = rootElement.getAttributeValue(USER);
      result.original = new Document((Element)((Element)(rootElement.getChildren().iterator().next())).clone());
    }
    else {
      result.name = rootElement.getAttributeValue(NAME);
      result.original = subDocument;
    }
    return result;
  }

  private boolean alreadyShared(final String subpath, final Collection<T> currentSchemeList) {
    for (T t : currentSchemeList) {
      if (t instanceof ExternalizableScheme) {
        ExternalInfo info = ((ExternalizableScheme)t).getExternalInfo();
        if (info.isIsImported()) {
          if (getFileFullPath(subpath).equals(info.getOriginalPath())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private String getFileFullPath(final String subpath) {
    return myFileSpec + "/" + subpath;
  }

  public void exportScheme(final E scheme, final String name, final String description) throws WriteExternalException {
    final StreamProvider[] providers = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager()
        .getStreamProviders(RoamingType.GLOBAL);
    if (providers != null) {
      Document document = myProcessor.writeScheme(scheme);
      if (document != null) {
        Document wrapped = wrap(document, name, description);
        for (StreamProvider provider : providers) {
          if (provider instanceof CurrentUserHolder) {
            wrapped = (Document)wrapped.clone();
            String userName = ((CurrentUserHolder)provider).getCurrentUserName();
            if (userName != null) {
              wrapped.getRootElement().setAttribute(USER, userName);
            }
          }
          try {
            provider.saveContent(getFileFullPath(UniqueFileNamesProvider.convertName(scheme.getName())) + EXT, wrapped, RoamingType.GLOBAL);
          }
          catch (IOException e) {
            LOG.debug(e);
          }
        }
      }
    }

  }

  private Document wrap(final Document original, final String name, final String description) {
    Element sharedElement = new Element(SHARED_SCHEME_ORIGINAL);
    sharedElement.setAttribute(NAME, name);
    sharedElement.setAttribute(DESCRIPTION, description);
    sharedElement.addContent(((Element)original.getRootElement().clone()));
    return new Document(sharedElement);
  }

  public boolean isImportAvailable() {
    final StreamProvider[] providers = ((ApplicationImpl)ApplicationManager.getApplication()).getStateStore().getStateStorageManager()
        .getStreamProviders(RoamingType.GLOBAL);

    if (providers == null) return false;

    for (StreamProvider provider : providers) {
      if (provider.isEnabled()) return true;
    }

    return false;
  }

  public boolean isExportAvailable() {
    return EXPORT_IS_AVAILABLE;
  }

  public boolean isShared(final Scheme scheme) {
    return scheme instanceof ExternalizableScheme && ((ExternalizableScheme)scheme).getExternalInfo().isIsImported();
  }

  public void save() throws WriteExternalException {
    try {
      myIsUpdating = true;
      Collection<T> schemes = getAllSchemes();
      myBaseDir.mkdirs();


      UniqueFileNamesProvider fileNameProvider = new UniqueFileNamesProvider();

      reserveUsingFileNames(schemes, fileNameProvider);

      deleteFilesFromDeletedSchemes();
      saveSchemes(schemes, fileNameProvider);

      if (myDeletedNames.size() > 0) {
        for (StreamProvider provider : getEnabledProviders()) {
          try {
            provider.saveContent(getFileFullPath(DELETED_XML), createDeletedDocument(), myRoamingType);
          }
          catch (IOException e) {
            LOG.debug(e);
          }
        }
      }
      else {
        for (StreamProvider provider : getEnabledProviders()) {
          provider.deleteFile(getFileFullPath(DELETED_XML), myRoamingType);
        }

      }
    }
    finally {
      myIsUpdating = false;
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
    for (StreamProvider provider : getEnabledProviders()) {
      provider.deleteFile(getFileFullPath(file.getName()), myRoamingType);
    }
  }

  private void saveSchemes(final Collection<T> schemes, final UniqueFileNamesProvider fileNameProvider)
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

            saveIfNeeded(eScheme, file, document, newHash, oldHash);
          }
          catch (final IOException e) {
            ApplicationManager.getApplication().invokeLater(
                new Runnable(){
                  public void run() {
                    String msg = "Cannot write scheme " + file.getName() + " in '" + myFileSpec + "': " + e.getLocalizedMessage();
                    LOG.warn(msg, e);
                    Messages.showErrorDialog(msg, "Save Settings");
                  }
                }
            );

          }
        }


      }


    }
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

  private void saveIfNeeded(final E schemeKey, final File file, final Document document, final long newHash, final Long oldHash) throws IOException {
    if (oldHash == null || newHash != oldHash.longValue() || !file.isFile()) {
      if (oldHash != null && newHash != oldHash.longValue()) {
        final byte[] text = StorageUtil.printDocument(document);

        if (needsSave(file, text)) {
          FileUtil.writeToFile(file, text);
        }

      }
      else {
        byte[] text = StorageUtil.printDocument(document);
        if (needsSave(file, text)) {
          FileUtil.writeToFile(file, text);
        }
      }
      schemeKey.getExternalInfo().setHash(newHash);
      saveFileName(file, schemeKey);

      saveOnServer(file, document);
    }
  }

  private boolean needsSave(final File file, final byte[] text) throws IOException {
    return !file.isFile() || !Arrays.equals(FileUtil.loadFileBytes(file), text);
  }

  private void saveOnServer(final File file, final Document document) {
    for (StreamProvider provider : getEnabledProviders()) {
      try {
        provider.saveContent(getFileFullPath(file.getName()), document, myRoamingType);
      }
      catch (IOException e) {
        LOG.debug(e);
      }
    }
  }

  private Collection<StreamProvider> getEnabledProviders() {
    ArrayList<StreamProvider> result = new ArrayList<StreamProvider>();
    for (StreamProvider provider : myProviders) {
      if (provider.isEnabled()) {
        result.add(provider);
      }
    }
    return result;
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
