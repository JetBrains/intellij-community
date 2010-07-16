package com.intellij.ide.util.frameworkSupport;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.ui.FacetBasedFrameworkSupportProvider;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportNode;
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
  private FrameworkSupportModelImpl myFrameworkSupportModel;
  private Map<FrameworkSupportProvider, FrameworkSupportConfigurable> myConfigurables;
  private Map<FrameworkSupportProvider, FrameworkSupportNode> myNodes;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFrameworkSupportModel = new FrameworkSupportModelImpl(getProject(), null);
    myNodes = new HashMap<FrameworkSupportProvider, FrameworkSupportNode>();
    for (FrameworkSupportProvider provider : FrameworkSupportProvider.EXTENSION_POINT.getExtensions()) {
      final FrameworkSupportNode node = new FrameworkSupportNode(provider, null, myFrameworkSupportModel, null, getTestRootDisposable());
      myNodes.put(provider, node);
      myFrameworkSupportModel.registerComponent(provider, node);
    }
    myConfigurables = new HashMap<FrameworkSupportProvider, FrameworkSupportConfigurable>();
  }

  protected void addSupport() throws IOException {
    final VirtualFile root = getVirtualFile(createTempDir("contentRoot"));
    PsiTestUtil.addContentRoot(myModule, root);
    final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
    try {
      List<FrameworkSupportConfigurable> selectedConfigurables = new ArrayList<FrameworkSupportConfigurable>();
      for (FrameworkSupportNode node : myNodes.values()) {
        if (node.isChecked()) {
          final FrameworkSupportConfigurable configurable = getOrCreateConfigurable(node.getProvider());
          configurable.addSupport(myModule, model, null);
          selectedConfigurables.add(configurable);
        }
      }
      for (FrameworkSupportCommunicator communicator : FrameworkSupportCommunicator.EP_NAME.getExtensions()) {
        communicator.onFrameworkSupportAdded(myModule, model, selectedConfigurables, myFrameworkSupportModel);
      }
    }
    finally {
      model.commit();
    }
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
    myNodes.get(provider).setChecked(true);
    configurable.onFrameworkSelectionChanged(true);
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
