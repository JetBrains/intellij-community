package com.intellij.ide.util.frameworkSupport;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.ui.FacetBasedFrameworkSupportProvider;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportCommunicator;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

/**
 * @author nik
 */
public abstract class FrameworkSupportProviderTestCase extends IdeaTestCase {
  private FrameworkSupportModel myFrameworkSupportModel;
  private Map<FrameworkSupportProvider, FrameworkSupportConfigurable> myConfigurables;
  private Set<FrameworkSupportProvider> mySelected;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFrameworkSupportModel = new FrameworkSupportModelImpl(getProject(), null);
    myConfigurables = new HashMap<FrameworkSupportProvider, FrameworkSupportConfigurable>();
    mySelected = new HashSet<FrameworkSupportProvider>();
  }

  protected void addSupport() throws IOException {
    final VirtualFile root = getVirtualFile(createTempDir("contentRoot"));
    PsiTestUtil.addContentRoot(myModule, root);
    final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
    List<FrameworkSupportConfigurable> selectedConfigurables = new ArrayList<FrameworkSupportConfigurable>();
    for (FrameworkSupportProvider provider : mySelected) {
      final FrameworkSupportConfigurable configurable = myConfigurables.get(provider);
      configurable.addSupport(myModule, model, null);
      selectedConfigurables.add(configurable);
    }
    for (FrameworkSupportCommunicator communicator : FrameworkSupportCommunicator.EP_NAME.getExtensions()) {
      communicator.onFrameworkSupportAdded(myModule, model, selectedConfigurables, myFrameworkSupportModel);
    }
    model.commit();
    for (FrameworkSupportConfigurable configurable : myConfigurables.values()) {
      Disposer.dispose(configurable);
    }
  }

  protected FrameworkSupportConfigurable selectFramework(@NotNull FacetTypeId<?> id) {
    return selectFramework(FacetBasedFrameworkSupportProvider.getProviderId(id));
  }

  protected FrameworkSupportConfigurable selectFramework(@NotNull String id) {
    for (FrameworkSupportProvider provider : FrameworkSupportProvider.EXTENSION_POINT.getExtensions()) {
      if (provider.getId().equals(id)) {
        return selectFramework(provider);
      }
    }
    fail("Framework provider with id='" + id + "' not found");
    return null;
  }

  protected FrameworkSupportConfigurable selectFramework(@NotNull FrameworkSupportProvider provider) {
    final FrameworkSupportConfigurable configurable = getOrCreateConfigurable(provider);
    configurable.onFrameworkSelectionChanged(true);
    mySelected.add(provider);
    return configurable;
  }

  private FrameworkSupportConfigurable getOrCreateConfigurable(FrameworkSupportProvider provider) {
    FrameworkSupportConfigurable configurable = myConfigurables.get(provider);
    if (configurable == null) {
      configurable = provider.createConfigurable(myFrameworkSupportModel);
      myConfigurables.put(provider, configurable);
    }
    return configurable;
  }

  protected <F extends Facet> F getFacet(FacetTypeId<F> id) {
    final F facet = FacetManager.getInstance(myModule).getFacetByType(id);
    assertNotNull(id + " facet not found", facet);
    return facet;
  }

  protected VirtualFile getContentRoot() {
    return ModuleRootManager.getInstance(myModule).getContentRoots()[0];
  }
}
