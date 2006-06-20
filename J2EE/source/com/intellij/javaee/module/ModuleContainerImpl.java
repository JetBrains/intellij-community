package com.intellij.javaee.module;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.watcher.ModuleRootsWatcher;
import com.intellij.openapi.roots.watcher.ModuleRootsWatcherFactory;
import com.intellij.openapi.util.*;
import com.intellij.util.ExternalizableString;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Alexey Kudravtsev
 */
public class ModuleContainerImpl implements ModuleContainer {
  private static final Logger LOG = Logger.getInstance("#com.intellij.javaee.module.J2EEModuleContainerImpl");
  private ModulesProvider myDefaultModulesProvider;
  private ModuleContainerImpl myModifiableModel;
  protected final Module myParentModule;
  private final Set<ContainerElement> myContents = new LinkedHashSet<ContainerElement>();

  /**
   * @deprecated
   */
  private ModuleRootsWatcher<ExternalizableString> myModuleRootsWatcher;
  private Map<ExternalizableString, OrderEntryInfo> myOrderInfo;
  @NonNls private static final String TYPE_ATTRIBUTE_NAME = "type";
  @NonNls private static final String CONTAINER_ELEMENT_NAME = "containerElement";
  @NonNls private static final String MODULE_TYPE = "module";
  @NonNls private static final String LIBRARY_TYPE = "library";
  @NonNls protected static final String WATCHER_ELEMENT_NAME = "orderEntriesWatcher";
  @NonNls protected static final String ENTRY_INFO_ELEMENT_NAME = "order-entry-info";
  @NonNls protected static final String INFO_ELEMENT_NAME = "info";
  @NonNls protected static final String KEY_ELEMENT_NAME = "key";
  @NonNls protected static final String VALUE_ELEMENT_NAME = "value";

  public ModuleContainerImpl(Module module) {
    LOG.assertTrue(module != null);
    myParentModule = module;
    myDefaultModulesProvider = new DefaultModulesProvider(module.getProject());
    initContainer();
  }

  public void removeLibrary(final Library library) {
    for (ContainerElement content : myContents) {
      if (content instanceof LibraryLink && library.equals(((LibraryLink)content).getLibrary())) {
        myContents.remove(content);
        break;
      }
    }
  }

  protected void initContainer() {
    if (myModuleRootsWatcher != null) {
      migrateRootsWatcher();
    }
  }

  private void migrateRootsWatcher() {
    final Set<ExternalizableString> keys = myOrderInfo.keySet();
    for (final ExternalizableString key : keys) {
      final OrderEntryInfo orderEntryInfo = myOrderInfo.get(key);
      if (!orderEntryInfo.copy) continue;
      final OrderEntry orderEntry = myModuleRootsWatcher.find(getModule(), key);
      if (orderEntry == null) continue;
      final ContainerElement containerElement;
      if (orderEntry instanceof ModuleOrderEntry) {
        final Module module = ((ModuleOrderEntry)orderEntry).getModule();
        if (module == null) continue;
        containerElement = new ModuleLinkImpl(module, getModule());
        containerElement.setPackagingMethod(getModule().getModuleType().equals(ModuleType.EJB)
                                            ? J2EEPackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST
                                            : J2EEPackagingMethod.COPY_FILES);
      }
      else if (orderEntry instanceof LibraryOrderEntry) {
        final Library library = ((LibraryOrderEntry)orderEntry).getLibrary();
        if (library == null) continue;
        containerElement = new LibraryLinkImpl(library, getModule());
        containerElement.setPackagingMethod(getModule().getModuleType().equals(ModuleType.EJB)
                                            ? J2EEPackagingMethod.COPY_FILES_AND_LINK_VIA_MANIFEST
                                            : J2EEPackagingMethod.COPY_FILES);
      }
      else {
        LOG.error("invalid type " + orderEntry);
        continue;
      }
      containerElement.setURI(orderEntryInfo.URI);
      final Map<String, String> attributes = orderEntryInfo.getAttributes();
      for (final String name : attributes.keySet()) {
        String value = attributes.get(name);
        containerElement.setAttribute(name, value);
      }
      containerElement.setURI(orderEntryInfo.URI);
      addElement(containerElement);
    }


  }

  public void readExternal(Element element) throws InvalidDataException {
    clearContainer();
    final List<Element> children = element.getChildren(CONTAINER_ELEMENT_NAME);
    for (Element child : children) {
      final String type = child.getAttributeValue(TYPE_ATTRIBUTE_NAME);
      ContainerElement containerElement;
      if (MODULE_TYPE.equals(type)) {
        containerElement = new ModuleLinkImpl((String)null, getModule());
      }
      else if (LIBRARY_TYPE.equals(type)) {
        containerElement = new LibraryLinkImpl(null, getModule());
      }
      else {
        throw new InvalidDataException("invalid type: " + type + " " + child);
      }

      containerElement.readExternal(child);
      addElement(containerElement);
    }
    if (children.size() == 0) {
      readPlainOldWatcherEntries(element);
    }
  }

  private static class ExternalizableStringFactory implements Factory<ExternalizableString> {
    private int seed;

    public ExternalizableString create() {
      return new ExternalizableString(String.valueOf(seed++));
    }

    private void expandSeed(ExternalizableString orderEntryKey) {
      try {
        int i = Integer.parseInt(orderEntryKey.value);
        seed = Math.max(i + 1, seed);
      }
      catch (NumberFormatException e) {
        // must be syntetic entry
      }
    }
  }

  /**
   * @deprecated
   */
  private void readPlainOldWatcherEntries(Element element) throws InvalidDataException {
    final Element watcher = element.getChild(WATCHER_ELEMENT_NAME);
    if (watcher == null) {
      return;
    }
    final ExternalizableStringFactory factory = new ExternalizableStringFactory();
    myModuleRootsWatcher = ModuleRootsWatcherFactory.create(factory);
    myModuleRootsWatcher.readExternal(watcher);
    myOrderInfo = new HashMap<ExternalizableString, OrderEntryInfo>();
    final Element infoRoot = element.getChild(ENTRY_INFO_ELEMENT_NAME);
    if (infoRoot != null) {
      final List infos = infoRoot.getChildren(INFO_ELEMENT_NAME);
      for (Object info1 : infos) {
        Element info = (Element)info1;
        final Element keyElement = info.getChild(KEY_ELEMENT_NAME);
        final ExternalizableString key = new ExternalizableString("");
        key.readExternal(keyElement);
        final Element valueElement = info.getChild(VALUE_ELEMENT_NAME);
        final OrderEntryInfo value = new OrderEntryInfo();
        value.readExternal(valueElement);
        // the only situation we want to change seed
        factory.expandSeed(key);

        myOrderInfo.put(key, value);
      }
    }

  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (final ContainerElement containerElement : myContents) {
      final Element child = new Element(CONTAINER_ELEMENT_NAME);
      if (containerElement instanceof ModuleLink) {
        child.setAttribute(TYPE_ATTRIBUTE_NAME, MODULE_TYPE);
      }
      else if (containerElement instanceof LibraryLink) {
        child.setAttribute(TYPE_ATTRIBUTE_NAME, LIBRARY_TYPE);
      }
      else {
        throw new WriteExternalException("invalid type: " + containerElement);
      }
      containerElement.writeExternal(child);
      element.addContent(child);
    }
  }

  public ModuleLink[] getContainingModules() {

    final List<ModuleLink> moduleLinks = new ArrayList<ModuleLink>();

    ContainerElement[] elements = getElements();
    for (ContainerElement element : elements) {
      if (element instanceof ModuleLink) {
        moduleLinks.add((ModuleLink)element);
      }
    }

    return moduleLinks.toArray(new ModuleLink[moduleLinks.size()]);
  }

  @Nullable
  public ModuleLink findModuleLink(final Module module) {
    return ContainerUtil.find(getContainingModules(), new Condition<ModuleLink>() {
      public boolean value(final ModuleLink object) {
        return object.getModule() == module;
      }
    });
  }

  public LibraryLink[] getContainingLibraries() {
    final List<LibraryLink> libraryLinks = new ArrayList<LibraryLink>();
    ContainerElement[] elements = getElements();
    for (ContainerElement element : elements) {
      if (element instanceof LibraryLink) {
        libraryLinks.add((LibraryLink)element);
      }
    }
    return libraryLinks.toArray(new LibraryLink[libraryLinks.size()]);
  }

  public ContainerElement[] getElements() {
    return getElements(myDefaultModulesProvider, true, false, false);
  }

  public ContainerElement[] getAllElements() {
    return getElements(myDefaultModulesProvider, true, true, true);
  }

  public ContainerElement[] getElements(ModulesProvider provider, final boolean includeResolved, final boolean includeUnresolved, final boolean includeNonPackaged) {
    ArrayList<ContainerElement> result = new ArrayList<ContainerElement>();
    for (final ContainerElement containerElement : myContents) {
      final boolean resolved = containerElement.resolveElement(provider);
      if ((resolved && includeResolved || !resolved && includeUnresolved)
        && (includeNonPackaged || containerElement.getPackagingMethod() != J2EEPackagingMethod.DO_NOT_PACKAGE)) {
        result.add(containerElement);
      }
    }
    return result.toArray(new ContainerElement[result.size()]);
  }

  public void setElements(ContainerElement[] elements) {
    clearContainer();
    myContents.addAll(Arrays.asList(elements));
  }

  public void removeModule(Module module) {
    for (final ContainerElement containerElement : myContents) {
      if (containerElement instanceof ModuleLink && ((ModuleLink)containerElement).getModule() == module) {
        myContents.remove(containerElement);
        break;
      }
    }
  }

  public void containedEntriesChanged() {

  }

  public Module getModule() {
    return myParentModule;
  }

  private void clearContainer() {
    myContents.clear();
  }

  public void copyFrom(ModuleContainer from, ModifiableRootModel rootModel) {
    copyContainerInfoFrom((ModuleContainerImpl)from);
  }

  public final ModuleContainer getModifiableModel() {
    return myModifiableModel;
  }

  public void commit(ModifiableRootModel model) throws ConfigurationException {
    if (isModified(model)) {
      copyContainerInfoFrom(myModifiableModel);
      containedEntriesChanged();
    }
  }

  public void disposeModifiableModel() {
    myModifiableModel = null;
  }

  public void startEdit(ModifiableRootModel rootModel) {
    myModifiableModel = new ModuleContainerImpl(getModule());
    myModifiableModel.copyFrom(this, rootModel);
  }

  public boolean isModified(ModifiableRootModel model) {
    final ContainerElement[] modifiedElements = myModifiableModel.getAllElements();

    return !Arrays.equals(modifiedElements, getAllElements());
  }

  private void copyContainerInfoFrom(ModuleContainerImpl from) {
    clearContainer();
    final ContainerElement[] elements = from.getAllElements();
    for (final ContainerElement element : elements) {
      addElement(element.clone());
    }
  }

  public void addElement(ContainerElement element) {
    myContents.add(element);
  }

}
