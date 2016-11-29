package com.intellij.ide.util.frameworkSupport;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.ui.FacetBasedFrameworkSupportProvider;
import com.intellij.framework.FrameworkType;
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.newProjectWizard.FrameworkSupportNode;
import com.intellij.ide.util.newProjectWizard.OldFrameworkSupportProviderWrapper;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportCommunicator;
import com.intellij.ide.util.newProjectWizard.impl.FrameworkSupportModelBase;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.IdeaModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainerFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.PsiTestUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public abstract class FrameworkSupportProviderTestCase extends IdeaTestCase {
  private FrameworkSupportModelBase myFrameworkSupportModel;
  private Map<FrameworkType, FrameworkSupportInModuleConfigurable> myConfigurables;
  private Map<FrameworkType, FrameworkSupportNode> myNodes;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final Project project = getProject();
    myFrameworkSupportModel = new FrameworkSupportModelImpl(project, "", LibrariesContainerFactory.createContainer(project));
    myNodes = new LinkedHashMap<>();
    final List<FrameworkSupportInModuleProvider> providers = FrameworkSupportUtil.getAllProviders();
    Collections.sort(providers, FrameworkSupportUtil.getFrameworkSupportProvidersComparator(providers));
    for (FrameworkSupportInModuleProvider provider : providers) {
      final FrameworkSupportNode node = new FrameworkSupportNode(provider, null, myFrameworkSupportModel, getTestRootDisposable());
      myNodes.put(provider.getFrameworkType(), node);
      myFrameworkSupportModel.registerComponent(provider, node);
    }
    myConfigurables = new HashMap<>();
  }

  protected void addSupport() {
    new WriteCommandAction.Simple(getProject()) {
      @Override
      protected void run() throws Throwable {
        final VirtualFile root = getVirtualFile(createTempDir("contentRoot"));
        PsiTestUtil.addContentRoot(myModule, root);
        final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
        try {
          List<FrameworkSupportConfigurable> selectedConfigurables = new ArrayList<>();
          final IdeaModifiableModelsProvider modelsProvider = new IdeaModifiableModelsProvider();
          for (FrameworkSupportNode node : myNodes.values()) {
            if (node.isChecked()) {
              final FrameworkSupportInModuleConfigurable configurable = getOrCreateConfigurable(node.getUserObject());
              configurable.addSupport(myModule, model, modelsProvider);
              if (configurable instanceof OldFrameworkSupportProviderWrapper.FrameworkSupportConfigurableWrapper) {
                selectedConfigurables.add(((OldFrameworkSupportProviderWrapper.FrameworkSupportConfigurableWrapper)configurable).getConfigurable());
              }
            }
          }
          for (FrameworkSupportCommunicator communicator : FrameworkSupportCommunicator.EP_NAME.getExtensions()) {
            communicator.onFrameworkSupportAdded(myModule, model, selectedConfigurables, myFrameworkSupportModel);
          }
        }
        finally {
          model.commit();
        }
        for (FrameworkSupportInModuleConfigurable configurable : myConfigurables.values()) {
          Disposer.dispose(configurable);
        }
      }
    }.execute().throwException();
  }

  protected FrameworkSupportInModuleConfigurable selectFramework(@NotNull FacetTypeId<?> id) {
    return selectFramework(FacetBasedFrameworkSupportProvider.getProviderId(id));
  }

  protected FrameworkSupportInModuleConfigurable selectFramework(@NotNull String id) {
    final FrameworkSupportInModuleProvider provider = FrameworkSupportUtil.findProvider(id, FrameworkSupportUtil.getAllProviders());
    if (provider != null) {
      return selectFramework(provider);
    }
    fail("Framework provider with id='" + id + "' not found");
    return null;
  }

  protected FrameworkSupportInModuleConfigurable selectFramework(@NotNull FrameworkSupportInModuleProvider provider) {
    final FrameworkSupportInModuleConfigurable configurable = getOrCreateConfigurable(provider);
    myNodes.get(provider.getFrameworkType()).setChecked(true);
    configurable.onFrameworkSelectionChanged(true);
    return configurable;
  }

  private FrameworkSupportInModuleConfigurable getOrCreateConfigurable(FrameworkSupportInModuleProvider provider) {
    FrameworkSupportInModuleConfigurable configurable = myConfigurables.get(provider.getFrameworkType());
    if (configurable == null) {
      configurable = provider.createConfigurable(myFrameworkSupportModel);
      myConfigurables.put(provider.getFrameworkType(), configurable);
    }
    return configurable;
  }

  protected void selectVersion(FrameworkType frameworkType, com.intellij.framework.FrameworkVersion version) {
    myFrameworkSupportModel.setSelectedVersion(frameworkType.getId(), version);
  }

  @NotNull
  protected <F extends Facet> F getFacet(FacetTypeId<F> id) {
    final F facet = FacetManager.getInstance(myModule).getFacetByType(id);
    assertNotNull(id + " facet not found", facet);
    return facet;
  }

  protected VirtualFile getContentRoot() {
    return ModuleRootManager.getInstance(myModule).getContentRoots()[0];
  }
}
