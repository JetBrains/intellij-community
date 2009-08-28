package com.intellij.openapi.roots.impl;

import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleJdkOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.roots.RootProvider;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class ModuleJdkOrderEntryImpl extends LibraryOrderEntryBaseImpl implements WritableOrderEntry,
                                                                                  ClonableOrderEntry,
                                                                                  ModuleJdkOrderEntry,
                                                                                  ProjectJdkTable.Listener {
  @NonNls static final String ENTRY_TYPE = "jdk";
  @NonNls private static final String JDK_NAME_ATTR = "jdkName";
  @NonNls private static final String JDK_TYPE_ATTR = "jdkType";

  private Sdk myJdk;
  private String myJdkName;
  private String myJdkType;

  ModuleJdkOrderEntryImpl(@NotNull Sdk projectJdk,
                          RootModelImpl rootModel,
                          ProjectRootManagerImpl projectRootManager,
                          VirtualFilePointerManager filePointerManager) {
    super(rootModel, projectRootManager, filePointerManager);
    init(projectJdk, null, null);
  }

  ModuleJdkOrderEntryImpl(Element element,
                          RootModelImpl rootModel,
                          ProjectRootManagerImpl projectRootManager,
                          VirtualFilePointerManager filePointerManager) throws InvalidDataException {
    super(rootModel, projectRootManager, filePointerManager);
    if (!element.getName().equals(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME)) {
      throw new InvalidDataException();
    }
    final Attribute jdkNameAttribute = element.getAttribute(JDK_NAME_ATTR);
    if (jdkNameAttribute == null) {
      throw new InvalidDataException();
    }

    final String jdkName = jdkNameAttribute.getValue();
    final String jdkType = element.getAttributeValue(JDK_TYPE_ATTR);
    final ProjectJdkTable projectJdkTable = ProjectJdkTable.getInstance();
    final Sdk jdkByName = projectJdkTable.findJdk(jdkName, jdkType);
    if (jdkByName == null) {
      init ( null, jdkName, jdkType);
    }
    else {
      init ( jdkByName, null, null);
    }
  }



  private ModuleJdkOrderEntryImpl(ModuleJdkOrderEntryImpl that,
                                  RootModelImpl rootModel,
                                  ProjectRootManagerImpl projectRootManager,
                                  VirtualFilePointerManager filePointerManager) {
    super(rootModel, projectRootManager, filePointerManager);
    init(that.myJdk, that.getJdkName(), that.getJdkType());
  }

  public ModuleJdkOrderEntryImpl(final String jdkName,
                                 final String jdkType,
                                 final RootModelImpl rootModel,
                                 final ProjectRootManagerImpl projectRootManager,
                                 final VirtualFilePointerManager filePointerManager) {
    super(rootModel, projectRootManager, filePointerManager);
    init(null, jdkName, jdkType);
  }

  private void init(final Sdk jdk, final String jdkName, final String jdkType) {
    myJdk = jdk;
    setJdkName(jdkName);
    setJdkType(jdkType);
    init(getRootProvider());
    addListener();
  }

  private String getJdkType() {
    if (myJdk != null){
      return myJdk.getSdkType().getName();
    }
    return myJdkType;
  }

  private void addListener() {
    myProjectRootManagerImpl.addJdkTableListener(this);
  }

  private RootProvider getRootProvider() {
    if (myJdk != null) {
      return myJdk.getRootProvider();
    }
    else {
      return null;
    }
  }

  public Sdk getJdk() {
    return getRootModel().getConfigurationAccessor().getSdk(myJdk, myJdkName);
  }

  public String getJdkName() {
    if (myJdkName != null) return myJdkName;
    Sdk jdk = getJdk();
    if (jdk != null) {
      return jdk.getName();
    }
    return null;
  }

  public boolean isSynthetic() {
    return true;
  }


  public String getPresentableName() {
    if (myJdk != null) {
      return "< " + myJdk.getName() + " >";
    }
    else {
      return "< " + getJdkName() + " >";
    }
  }

  public boolean isValid() {
    return !isDisposed() && getJdk() != null;
  }

  public <R> R accept(RootPolicy<R> policy, R initialValue) {
    return policy.visitModuleJdkOrderEntry(this, initialValue);
  }

  public void jdkAdded(Sdk jdk) {
    if (myJdk == null && getJdkName().equals(jdk.getName())) {
      myJdk = jdk;
      setJdkName(null);
      setJdkType(null);
      updateFromRootProviderAndSubscribe(getRootProvider());
    }
  }

  public void jdkNameChanged(Sdk jdk, String previousName) {
    if (myJdk == null && getJdkName().equals(jdk.getName())) {
      myJdk = jdk;
      setJdkName(null);
      setJdkType(null);
      updateFromRootProviderAndSubscribe(getRootProvider());
    }
  }

  public void jdkRemoved(Sdk jdk) {
    if (jdk == myJdk) {
      setJdkName(myJdk.getName());
      setJdkType(myJdk.getSdkType().getName());
      myJdk = null;
      updateFromRootProviderAndSubscribe(getRootProvider());
    }
  }

  public void writeExternal(Element rootElement) throws WriteExternalException {
    final Element element = OrderEntryFactory.createOrderEntryElement(ENTRY_TYPE);
    final String jdkName = getJdkName();
    if (jdkName != null) {
      element.setAttribute(JDK_NAME_ATTR, jdkName);
    }
    final String jdkType = getJdkType();
    if (jdkType != null) {
      element.setAttribute(JDK_TYPE_ATTR, jdkType);
    }
    rootElement.addContent(element);
  }

  public OrderEntry cloneEntry(RootModelImpl rootModel,
                               ProjectRootManagerImpl projectRootManager,
                               VirtualFilePointerManager filePointerManager) {
    return new ModuleJdkOrderEntryImpl(this, rootModel, ProjectRootManagerImpl.getInstanceImpl(getRootModel().getModule().getProject()),
                                       VirtualFilePointerManager.getInstance());
  }

  public void dispose() {
    super.dispose();
    myProjectRootManagerImpl.removeJdkTableListener(this);
  }

  private void setJdkName(String jdkName) {
    myJdkName = jdkName;
  }

  private void setJdkType(String jdkType) {
    myJdkType = jdkType;
  }
}
