package com.intellij.openapi.fileTypes.impl;

import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExportableApplicationComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.*;
import com.intellij.openapi.options.SchemeReaderWriter;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PairConsumer;
import com.intellij.util.PatternUtil;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * @author Yura Cangea
 */
public class FileTypeManagerImpl extends FileTypeManagerEx implements NamedJDOMExternalizable, ExportableApplicationComponent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl");
  private static final int VERSION = 3;

  private final Set<FileType> myDefaultTypes = new THashSet<FileType>();
  private SetWithArray myFileTypes = new SetWithArray(new THashSet<FileType>());
  private final ArrayList<FileTypeIdentifiableByVirtualFile> mySpecialFileTypes = new ArrayList<FileTypeIdentifiableByVirtualFile>();
  private final ArrayList<Pattern> myIgnorePatterns = new ArrayList<Pattern>();

  private FileTypeAssocTable myPatternsTable = new FileTypeAssocTable();
  private final Set<String> myIgnoredFileMasksSet = new LinkedHashSet<String>();
  private final Set<String> myNotIgnoredFiles = new ConcurrentHashSet<String>();
  private final Set<String> myIgnoredFiles = new ConcurrentHashSet<String>();
  private final FileTypeAssocTable myInitialAssociations = new FileTypeAssocTable();
  private final Map<FileNameMatcher, String> myUnresolvedMappings = new THashMap<FileNameMatcher, String>();

  @NonNls private static final String ELEMENT_FILETYPE = "filetype";
  @NonNls private static final String ELEMENT_FILETYPES = "filetypes";
  @NonNls private static final String ELEMENT_IGNOREFILES = "ignoreFiles";
  @NonNls private static final String ATTRIBUTE_LIST = "list";
  @NonNls private static final String ELEMENT_EXTENSIONMAP = "extensionMap";
  @NonNls private static final String ELEMENT_MAPPING = "mapping";
  @NonNls private static final String ATTRIBUTE_EXT = "ext";
  @NonNls private static final String ATTRIBUTE_PATTERN = "pattern";
  @NonNls private static final String ATTRIBUTE_TYPE = "type";
  @NonNls private static final String ELEMENT_REMOVED_MAPPING = "removed_mapping";
  @NonNls private static final String ATTRIBUTE_VERSION = "version";
  @NonNls private static final String ATTRIBUTE_NAME = "name";
  @NonNls private static final String ATTRIBUTE_DESCRIPTION = "description";
  @NonNls private static final String ATTRIBUTE_ICON = "icon";
  @NonNls private static final String ATTRIBUTE_EXTENSIONS = "extensions";
  @NonNls private static final String ATTRIBUTE_BINARY = "binary";
  @NonNls private static final String ATTRIBUTE_DEFAULT_EXTENSION = "default_extension";
  @NonNls private static final String XML_EXTENSION = ".xml";
  private final MessageBus myMessageBus;
  private static final Map<String,Pair<FileType,String>> ourStandardFileTypes = new THashMap<String, Pair<FileType, String>>();
  @NonNls private static final String[] FILE_TYPES_WITH_PREDEFINED_EXTENSIONS = {"JSP", "JSPX", "DTD", "HTML", "Properties", "XHTML"};
  private final SchemesManager mySchemesManager;
  private static final String FILE_SPEC = "$ROOT_CONFIG$/filetypes";

  static {
    final PairConsumer<FileType, String> consumer = new PairConsumer<FileType, String>() {
      public void consume(final FileType fileType, final String extensions) {
        ourStandardFileTypes.put(fileType.getName(), Pair.create(fileType, extensions));
      }
    };
    for (final FileTypeFactory factory : Extensions.getExtensions(FileTypeFactory.FILE_TYPE_FACTORY_EP)) {
      factory.createFileTypes(consumer);
    }
  }

  // -------------------------------------------------------------------------
  // Constructor
  // -------------------------------------------------------------------------

  public FileTypeManagerImpl(MessageBus bus, SchemesManager schemesManager) {
    mySchemesManager = schemesManager;
    for (final Pair<FileType, String> pair : ourStandardFileTypes.values()) {
      registerFileTypeWithoutNotification(pair.first, parse(pair.second));
    }
    if (loadAllFileTypes()) {
      restoreStandardFileExtensions();
    }
    myMessageBus = bus;
  }

  @NotNull
  public FileType getStdFileType(@NotNull @NonNls String name) {
    Pair<FileType, String> pair = ourStandardFileTypes.get(name);
    return pair != null ? pair.first : new PlainTextFileType();
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{getOrCreateFileTypesDir(), PathManager.getOptionsFile(this)};
  }

  @NotNull
  public String getPresentableName() {
    return FileTypesBundle.message("filetype.settings.component");
  }
  // -------------------------------------------------------------------------
  // ApplicationComponent interface implementation
  // -------------------------------------------------------------------------

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public void save() {
    try {
      saveAllFileTypes();
    }
    catch (IOException e) {
      Messages.showErrorDialog(FileTypesBundle.message("filetype.settings.cannot.save.error", e.getLocalizedMessage()),
                               FileTypesBundle.message("filetype.settings.cannot.save.title"));
    }
  }

  // -------------------------------------------------------------------------
  // Implementation of abstract methods
  // -------------------------------------------------------------------------

  @NotNull
  public FileType getFileTypeByFileName(@NotNull String fileName) {
    FileType type = myPatternsTable.findAssociatedFileType(fileName);
    return type == null ? FileTypes.UNKNOWN : type;
  }

  @NotNull
  public FileType getFileTypeByFile(@NotNull VirtualFile file) {
    // first let file recognize its type
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < mySpecialFileTypes.size(); i++) {
      final FileTypeIdentifiableByVirtualFile fileType = mySpecialFileTypes.get(i);
      if (fileType.isMyFileType(file)) return fileType;
    }

    return getFileTypeByFileName(file.getName());
  }

  @NotNull
  public FileType getFileTypeByExtension(@NotNull String extension) {
    return getFileTypeByFileName("IntelliJ_IDEA_RULES." + extension);
  }

  public void registerFileType(FileType fileType) {
    registerFileType(fileType, new String[0]);
  }

  public void registerFileType(@NotNull final FileType type, @NotNull final List<FileNameMatcher> defaultAssociations) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        fireBeforeFileTypesChanged();
        registerFileTypeWithoutNotification(type, defaultAssociations);
        fireFileTypesChanged();

      }
    });
  }

  public void unregisterFileType(FileType fileType) {
    fireBeforeFileTypesChanged();
    unregisterFileTypeWithoutNotification(fileType);
    fireFileTypesChanged();
  }

  private void unregisterFileTypeWithoutNotification(FileType fileType) {
    removeAllAssociations(fileType);
    myFileTypes.remove(fileType);
    if (fileType instanceof FileTypeIdentifiableByVirtualFile) {
      final FileTypeIdentifiableByVirtualFile fakeFileType = (FileTypeIdentifiableByVirtualFile)fileType;
      mySpecialFileTypes.remove(fakeFileType);
    }
  }

  @NotNull
  public FileType[] getRegisteredFileTypes() {
    return myFileTypes.toArray();
  }

  @NotNull
  public String getExtension(String fileName) {
    int index = fileName.lastIndexOf('.');
    if (index < 0) return "";
    return fileName.substring(index + 1);
  }

  @NotNull
  public String getIgnoredFilesList() {
    StringBuilder sb = new StringBuilder();
    for (String ignoreMask : myIgnoredFileMasksSet) {
      sb.append(ignoreMask);
      sb.append(';');
    }
    return sb.toString();
  }

  public void setIgnoredFilesList(@NotNull String list) {
    fireBeforeFileTypesChanged();
    setIgnoredFilesListWithoutNotification(list);

    fireFileTypesChanged();
  }

  private void setIgnoredFilesListWithoutNotification(String list) {
    myIgnoredFileMasksSet.clear();
    myIgnorePatterns.clear();

    StringTokenizer tokenizer = new StringTokenizer(list, ";");
    while (tokenizer.hasMoreTokens()) {
      String ignoredFile = tokenizer.nextToken();
      if (ignoredFile != null && !myIgnoredFileMasksSet.contains(ignoredFile)) {
        if (!myIgnoredFileMasksSet.contains(ignoredFile)) {
          myIgnorePatterns.add(PatternUtil.fromMask(ignoredFile));
        }
        myIgnoredFileMasksSet.add(ignoredFile);
      }
    }

    //[mike]
    //To make async delete work. See FileUtil.asyncDelete.
    //Quite a hack, but still we need to have some name, which
    //won't be catched by VF for sure.
    //noinspection HardCodedStringLiteral
    Pattern p = Pattern.compile(".*\\.__del__");
    myIgnorePatterns.add(p);
  }

  public boolean isIgnoredFilesListEqualToCurrent(String list) {
    Set<String> tempSet = new THashSet<String>();
    StringTokenizer tokenizer = new StringTokenizer(list, ";");
    while (tokenizer.hasMoreTokens()) {
      tempSet.add(tokenizer.nextToken());
    }
    return tempSet.equals(myIgnoredFileMasksSet);
  }

  public boolean isFileIgnored(@NotNull String name) {
    if (myNotIgnoredFiles.contains(name)) return false;
    if (myIgnoredFiles.contains(name)) return true;

    for (Pattern pattern : myIgnorePatterns) {
      if (pattern.matcher(name).matches()) {
        myIgnoredFiles.add(name);
        return true;
      }
    }

    myNotIgnoredFiles.add(name);
    return false;
  }

  @SuppressWarnings({"deprecation"})
  @NotNull
  public String[] getAssociatedExtensions(@NotNull FileType type) {
    return myPatternsTable.getAssociatedExtensions(type);
  }

  @NotNull
  public List<FileNameMatcher> getAssociations(@NotNull FileType type) {
    return myPatternsTable.getAssociations(type);
  }

  public void associate(@NotNull FileType type, @NotNull FileNameMatcher matcher) {
    associate(type, matcher, true);
  }

  public void removeAssociation(@NotNull FileType type, @NotNull FileNameMatcher matcher) {
    removeAssociation(type, matcher, true);
  }

  private void removeAllAssociations(FileType type) {
    myPatternsTable.removeAllAssociations(type);
  }

  public void fireBeforeFileTypesChanged() {
    FileTypeEvent event = new FileTypeEvent(this);
    myMessageBus.syncPublisher(AppTopics.FILE_TYPES).beforeFileTypesChanged(event);
  }

  public void fireFileTypesChanged() {
    myNotIgnoredFiles.clear();
    myIgnoredFiles.clear();

    final FileTypeEvent event = new FileTypeEvent(this);
    myMessageBus.syncPublisher(AppTopics.FILE_TYPES).fileTypesChanged(event);
  }

  private final Map<FileTypeListener, MessageBusConnection> myAdapters = new HashMap<FileTypeListener, MessageBusConnection>();
  public void addFileTypeListener(@NotNull FileTypeListener listener) {
    final MessageBusConnection connection = myMessageBus.connect();
    connection.subscribe(AppTopics.FILE_TYPES, listener);
    myAdapters.put(listener, connection);
  }

  public void removeFileTypeListener(@NotNull FileTypeListener listener) {
    final MessageBusConnection connection = myAdapters.remove(listener);
    if (connection != null) {
      connection.disconnect();
    }
  }

  private void saveAllFileTypes() throws IOException {

    final List<FileType> fileTypes = Arrays.asList(myFileTypes.toArray());
    try {
      mySchemesManager.saveSchemes(fileTypes, FILE_SPEC,createSchemeProcessor(new boolean[]{false}), RoamingType.PER_USER);
    }
    catch (WriteExternalException e) {
      //ignore
    }

  }



  @SuppressWarnings({"SimplifiableIfStatement"})
  private static boolean isDefaultModified(FileType fileType) {
    if (fileType instanceof ExternalizableFileType) {
      return ((ExternalizableFileType) fileType).isModified();
    }
    return true; //TODO?
  }

  // -------------------------------------------------------------------------
  // Implementation of NamedExternalizable interface
  // -------------------------------------------------------------------------

  public String getExternalFileName() {
    return "filetypes";
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    int savedVersion = getVersion(parentNode);
    for (final Object o : parentNode.getChildren()) {
      final Element e = (Element)o;
      if (ELEMENT_FILETYPES.equals(e.getName())) {
        List children = e.getChildren(ELEMENT_FILETYPE);
        for (final Object aChildren : children) {
          Element element = (Element)aChildren;
          loadFileType(element, true);
        }
      }
      else if (ELEMENT_IGNOREFILES.equals(e.getName())) {
        setIgnoredFilesListWithoutNotification(e.getAttributeValue(ATTRIBUTE_LIST));
      }
      else if (ELEMENT_EXTENSIONMAP.equals(e.getName())) {
        List mappings = e.getChildren(ELEMENT_MAPPING);

        for (Object mapping1 : mappings) {
          Element mapping = (Element)mapping1;
          String ext = mapping.getAttributeValue(ATTRIBUTE_EXT);
          String pattern = mapping.getAttributeValue(ATTRIBUTE_PATTERN);
          String name = mapping.getAttributeValue(ATTRIBUTE_TYPE);
          FileType type = getFileTypeByName(name);

          FileNameMatcher matcher = ext != null ? new ExtensionFileNameMatcher(ext) : createFromPattern(pattern);

          if (type != null) {
            associate(type, matcher, false);
          }
          else {
            // Not yet loaded plugin could add the file type later.
            myUnresolvedMappings.put(matcher, name);
          }
        }

        List removedMappings = e.getChildren(ELEMENT_REMOVED_MAPPING);
        for (Object removedMapping : removedMappings) {
          Element mapping = (Element)removedMapping;
          String ext = mapping.getAttributeValue(ATTRIBUTE_EXT);
          String pattern = mapping.getAttributeValue(ATTRIBUTE_PATTERN);
          String name = mapping.getAttributeValue(ATTRIBUTE_TYPE);
          FileType type = getFileTypeByName(name);
          FileNameMatcher matcher = ext != null ? new ExtensionFileNameMatcher(ext) : createFromPattern(pattern);

          if (type != null) {
            removeAssociation(type, matcher, false);
          }
        }
      }
    }

    if (savedVersion == 0) {
      addIgnore(".svn");
    }
    if (savedVersion < 2) {
      restoreStandardFileExtensions();
    }
    if (savedVersion < VERSION) {
      addIgnore(".pyc");
      addIgnore(".pyo");
    }
  }

  private void addIgnore(@NonNls final String ignoreMask) {
    if (!myIgnoredFileMasksSet.contains(ignoreMask)) {
      myIgnorePatterns.add(PatternUtil.fromMask(ignoreMask));
      myIgnoredFileMasksSet.add(ignoreMask);
    }
  }

  private static FileNameMatcher createFromPattern(final String pattern) {
    if (pattern.contains("?") || pattern.contains("*")) {
      return new WildcardFileNameMatcher(pattern);
    }
    else {
      return new ExactFileNameMatcher(pattern);
    }
  }

  private void restoreStandardFileExtensions() {
    for (final String name : FILE_TYPES_WITH_PREDEFINED_EXTENSIONS) {
      final Pair<FileType, String> pair = ourStandardFileTypes.get(name);
      if (pair != null) {
        FileType fileType = pair.first;
        for (FileNameMatcher matcher : myPatternsTable.getAssociations(fileType)) {
          FileType defaultFileType = myInitialAssociations.findAssociatedFileType(matcher);
          if (defaultFileType != null && defaultFileType != fileType) {
            removeAssociation(fileType, matcher, false);
            associate(defaultFileType, matcher, false);
          }
        }

        for (FileNameMatcher matcher : myInitialAssociations.getAssociations(fileType)) {
          associate(fileType, matcher, false);
        }
      }
    }
  }

  private static int getVersion(final Element node) {
    final String verString = node.getAttributeValue(ATTRIBUTE_VERSION);
    if (verString == null) return 0;
    try {
      return Integer.parseInt(verString);
    }
    catch (NumberFormatException e) {
      return 0;
    }
  }

  public void writeExternal(Element parentNode) throws WriteExternalException {
    parentNode.setAttribute(ATTRIBUTE_VERSION, String.valueOf(VERSION));

    Element element = new Element(ELEMENT_IGNOREFILES);
    parentNode.addContent(element);
    element.setAttribute(ATTRIBUTE_LIST, getIgnoredFilesList());
    Element map = new Element(ELEMENT_EXTENSIONMAP);
    parentNode.addContent(map);

    for (FileType type : getRegisteredFileTypes()) {
      final List<FileNameMatcher> assocs = myPatternsTable.getAssociations(type);
      final Set<FileNameMatcher> defaultAssocs = new HashSet<FileNameMatcher>(myInitialAssociations.getAssociations(type));

      for (FileNameMatcher matcher : assocs) {
        if (defaultAssocs.contains(matcher)) {
          defaultAssocs.remove(matcher);
        }
        else if (shouldSave(type)) {
          Element mapping = new Element(ELEMENT_MAPPING);
          if (matcher instanceof ExtensionFileNameMatcher) {
            mapping.setAttribute(ATTRIBUTE_EXT, ((ExtensionFileNameMatcher)matcher).getExtension());
          }
          else if (matcher instanceof WildcardFileNameMatcher) {
            mapping.setAttribute(ATTRIBUTE_PATTERN, ((WildcardFileNameMatcher)matcher).getPattern());
          }
          else if (matcher instanceof ExactFileNameMatcher) {
            mapping.setAttribute(ATTRIBUTE_PATTERN, ((ExactFileNameMatcher)matcher).getFileName());
          }
          else {
            continue;
          }

          mapping.setAttribute(ATTRIBUTE_TYPE, type.getName());
          map.addContent(mapping);
        }
      }

      for (FileNameMatcher matcher : defaultAssocs) {
        Element mapping = new Element(ELEMENT_REMOVED_MAPPING);
        if (matcher instanceof ExtensionFileNameMatcher) {
          mapping.setAttribute(ATTRIBUTE_EXT, ((ExtensionFileNameMatcher)matcher).getExtension());
        }
        else if (matcher instanceof WildcardFileNameMatcher) {
          mapping.setAttribute(ATTRIBUTE_PATTERN, ((WildcardFileNameMatcher)matcher).getPattern());
        }
        else if (matcher instanceof ExactFileNameMatcher) {
          mapping.setAttribute(ATTRIBUTE_PATTERN, ((ExactFileNameMatcher)matcher).getFileName());
        }
        else {
          continue;
        }
        mapping.setAttribute(ATTRIBUTE_TYPE, type.getName());
        map.addContent(mapping);
      }
    }
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------

  @Nullable
  private FileType getFileTypeByName(String name) {
    Iterator<FileType> itr = myFileTypes.iterator();
    while (itr.hasNext()) {
      FileType fileType = itr.next();
      if (fileType.getName().equals(name)) return fileType;
    }
    return null;
  }

  private static List<FileNameMatcher> parse(@NonNls String semicolonDelimited) {
    if (semicolonDelimited == null) return Collections.emptyList();
    StringTokenizer tokenizer = new StringTokenizer(semicolonDelimited, ";", false);
    ArrayList<FileNameMatcher> list = new ArrayList<FileNameMatcher>();
    while (tokenizer.hasMoreTokens()) {
      list.add(new ExtensionFileNameMatcher(tokenizer.nextToken().trim()));
    }
    return list;
  }

  /**
   * Registers a standard file type. Doesn't notifyListeners any change events.
   */
  private void registerFileTypeWithoutNotification(FileType fileType, List<FileNameMatcher> matchers) {
    myFileTypes.add(fileType);
    for (FileNameMatcher matcher : matchers) {
      myPatternsTable.addAssociation(matcher, fileType);
      myInitialAssociations.addAssociation(matcher, fileType);
    }

    if (fileType instanceof FileTypeIdentifiableByVirtualFile) {
      mySpecialFileTypes.add((FileTypeIdentifiableByVirtualFile)fileType);
    }

    // Resolve unresolved mappings initialized before certain plugin initialized.
    for (FileNameMatcher matcher : new THashSet<FileNameMatcher>(myUnresolvedMappings.keySet())) {
      String name = myUnresolvedMappings.get(matcher);
      if (Comparing.equal(name, fileType.getName())) {
        myPatternsTable.addAssociation(matcher, fileType);
        myUnresolvedMappings.remove(matcher);
      }
    }
  }

  private static File[] getFileTypeFiles() {
    File fileTypesDir = getOrCreateFileTypesDir();
    if (fileTypesDir == null) return new File[0];

    File[] files = fileTypesDir.listFiles(new FileFilter() {
      public boolean accept(File file) {
        return !file.isDirectory() && StringUtil.endsWithIgnoreCase(file.getName(), XML_EXTENSION);
      }
    });
    if (files == null) {
      LOG.error("Cannot read directory: " + fileTypesDir.getAbsolutePath());
      return new File[0];
    }
//    return files;
    ArrayList<File> fileList = new ArrayList<File>();
    for (File file : files) {
      if (!file.isDirectory()) {
        fileList.add(file);
      }
    }
    return fileList.toArray(new File[fileList.size()]);
  }

  // returns true if at least one standard file type has been read
  @SuppressWarnings({"EmptyCatchBlock"})
  private boolean loadAllFileTypes() {
    final boolean[] standardFileTypeRead = new boolean[] {false};

    mySchemesManager.loadSchemes(FILE_SPEC, createSchemeProcessor(standardFileTypeRead), RoamingType.PER_USER);

    return standardFileTypeRead[0];
  }

  private SchemeReaderWriter<FileType> createSchemeProcessor(final boolean[] standardFileTypeRead) {
    return new SchemeReaderWriter<FileType>(){
      public FileType readScheme(final Document document, final File file) throws InvalidDataException, IOException, JDOMException {
        if (document == null) {
          throw new InvalidDataException();
        }
        Element root = document.getRootElement();
        if (root == null || !ELEMENT_FILETYPE.equals(root.getName())) {
          throw new InvalidDataException();
        }
        final FileType fileType = loadFileType(root, false);
        standardFileTypeRead[0] |= myInitialAssociations.hasAssociationsFor(fileType);
        return fileType;

      }

      public boolean shouldBeSaved(final FileType fileType) {
        if (!(fileType instanceof JDOMExternalizable) || !shouldSave(fileType)) return false;
        if (myDefaultTypes.contains(fileType) && !isDefaultModified(fileType)) return false;
        return true;

      }

      public void showReadErrorMessage(final Exception e, final String schemeName, final String filePath) {
        //ignore
      }

      public void showWriteErrorMessage(final Exception e, final String schemeName, final String filePath) {
        //ignore
      }

      public Document writeScheme(final FileType fileType) throws WriteExternalException {
        Element root = new Element(ELEMENT_FILETYPE);

        writeHeader(root, fileType);

        ((JDOMExternalizable) fileType).writeExternal(root);

        return new Document(root);

      }
    };
  }

  private FileType loadFileType(Element typeElement, boolean isDefaults) {
    String fileTypeName = typeElement.getAttributeValue(ATTRIBUTE_NAME);
    String fileTypeDescr = typeElement.getAttributeValue(ATTRIBUTE_DESCRIPTION);
    String iconPath = typeElement.getAttributeValue(ATTRIBUTE_ICON);
    String extensionsStr = typeElement.getAttributeValue(ATTRIBUTE_EXTENSIONS); // TODO: support wildcards

    FileType type = getFileTypeByName(fileTypeName);

    List<FileNameMatcher> exts = parse(extensionsStr);
    if (type != null) {
      if (isDefaults) return type;
      if (extensionsStr != null) {
        removeAllAssociations(type);
        for (FileNameMatcher ext : exts) {
          associate(type, ext, false);
        }
      }

      if (type instanceof JDOMExternalizable) {
        try {
          ((JDOMExternalizable) type).readExternal(typeElement);
        }
        catch (InvalidDataException e) {
          throw new RuntimeException(e);
        }
      }
    }
    else {
      for(CustomFileTypeFactory factory: Extensions.getExtensions(CustomFileTypeFactory.EP_NAME)) {
        type = factory.createFileType(typeElement);
        if (type != null) {
          break;
        }
      }
      if (type == null) {
        type = new UserBinaryFileType();
      }
      registerFileTypeWithoutNotification(type, exts);
    }

    if (type instanceof UserFileType) {
      UserFileType ft = (UserFileType)type;
      if (iconPath != null && !"".equals(iconPath.trim())) {
        Icon icon = IconLoader.getIcon(iconPath);
        ft.setIcon(icon);
      }

      if (fileTypeDescr != null) ft.setDescription(fileTypeDescr);
      if (fileTypeName != null) ft.setName(fileTypeName);
    }

    if (isDefaults) {
      myDefaultTypes.add(type);
      if (type instanceof ExternalizableFileType) {
        ((ExternalizableFileType) type).markDefaultSettings();
      }
    }

    return type;
  }

  private static File getOrCreateFileTypesDir() {
    String directoryPath = PathManager.getConfigPath() + File.separator + ELEMENT_FILETYPES;
    File directory = new File(directoryPath);
    if (!directory.exists()) {
      if (!directory.mkdir()) {
        LOG.error("Could not create directory: " + directory.getAbsolutePath());
        return null;
      }
    }
    return directory;
  }

  private static boolean shouldSave(FileType fileType) {
    return fileType != FileTypes.UNKNOWN && !fileType.isReadOnly();
  }

  private static void writeHeader(Element root, FileType fileType) {
    root.setAttribute(ATTRIBUTE_BINARY, String.valueOf(fileType.isBinary()));
    root.setAttribute(ATTRIBUTE_DEFAULT_EXTENSION, fileType.getDefaultExtension());

    root.setAttribute(ATTRIBUTE_DESCRIPTION, fileType.getDescription());
    root.setAttribute(ATTRIBUTE_NAME, fileType.getName());
  }

  // -------------------------------------------------------------------------
  // Setup
  // -------------------------------------------------------------------------

  @NotNull
  public String getComponentName() {
    return "FileTypeManager";
  }

  public FileTypeAssocTable getExtensionMap() {
    return myPatternsTable;
  }

  public void setPatternsTable(Set<FileType> fileTypes, FileTypeAssocTable assocTable) {
    fireBeforeFileTypesChanged();
    myFileTypes = new SetWithArray(fileTypes);
    myPatternsTable = assocTable.copy();
    fireFileTypesChanged();
  }

  private static class SetWithArray {
    private final Set<FileType> mySet;
    private FileType[] myArray;

    public SetWithArray(Set<FileType> set) {
      mySet = set;
    }

    public void add(FileType element) {
      myArray = null;
      mySet.add(element);
    }

    public void remove(FileType element) {
      myArray = null;
      mySet.remove(element);
    }

    public Iterator<FileType> iterator() {
      final Iterator<FileType> iterator = mySet.iterator();
      return new Iterator<FileType>() {
        public boolean hasNext() {
          return iterator.hasNext();
        }

        public FileType next() {
          return iterator.next();
        }

        public void remove() {
          myArray = null;
          iterator.remove();
        }
      };
    }

    public FileType[] toArray() {
      if (myArray == null) myArray = mySet.toArray(new FileType[mySet.size()]);
      FileType[] array = new FileType[myArray.length];
      System.arraycopy(myArray, 0, array, 0, array.length);
      return array;
    }
  }

  public void associate(FileType fileType, FileNameMatcher matcher, boolean fireChange) {
    if (!myPatternsTable.isAssociatedWith(fileType, matcher)) {
      if (fireChange) {
        fireBeforeFileTypesChanged();
      }
      myPatternsTable.addAssociation(matcher, fileType);
      if (fireChange) {
        fireFileTypesChanged();
      }
    }
  }

  public void removeAssociation(FileType fileType, FileNameMatcher matcher, boolean fireChange){
    if (myPatternsTable.isAssociatedWith(fileType, matcher)) {
      if (fireChange) {
        fireBeforeFileTypesChanged();
      }
      myPatternsTable.removeAssociation(matcher, fileType);
      if (fireChange){
        fireFileTypesChanged();
      }
    }
  }

  @Nullable
  public FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file) {
    return FileTypeChooser.getKnownFileTypeOrAssociate(file);
  }
}
