// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.annotations.SerializedName;
import com.intellij.codeInsight.navigation.ListBackgroundUpdaterTask;
import com.intellij.execution.Executor;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.find.FindUtil;
import com.intellij.find.actions.CompositeActiveComponent;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.ui.popup.HintUpdateSupply;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PsiNavigateUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.RequestBuilder;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR;
import static com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE;

public class FindTestsInTestDiscoveryServerAction extends AnAction {
  private static final Logger LOG = Logger.getInstance(FindTestsInTestDiscoveryServerAction.class);

  @Override
  public void update(AnActionEvent e) {
    Editor editor = e.getData(EDITOR);
    PsiFile file = e.getData(PSI_FILE);
    Project project = e.getProject();

    PsiElement at = file == null || editor == null ? null : file.findElementAt(editor.getCaretModel().getOffset());
    PsiMethod method = PsiTreeUtil.getParentOfType(at, PsiMethod.class);
    if (method == null || project == null) {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Editor editor = e.getData(EDITOR);
    PsiFile file = e.getData(PSI_FILE);
    Project project = e.getProject();

    assert project != null;

    PsiElement at = file == null || editor == null ? null : file.findElementAt(editor.getCaretModel().getOffset());
    PsiMethod method = PsiTreeUtil.getParentOfType(at, PsiMethod.class);
    assert method != null;
    PsiClass c = method.getContainingClass();
    String fqn = c != null ? c.getQualifiedName() : null;
    if (fqn == null) return;
    String methodName = method.getName();
    String methodPresentationName = c.getName() + "." + methodName;

    CollectionListModel<PsiElement> model = new CollectionListModel<>();
    final JBList<PsiElement> list = new JBList<>(model);
    //list.setFixedCellHeight();
    HintUpdateSupply.installSimpleHintUpdateSupply(list);
    list.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

    String initTitle = "Tests for " + methodPresentationName;
    DefaultPsiElementCellRenderer renderer = new DefaultPsiElementCellRenderer();

    InplaceButton runButton = new InplaceButton(new IconButton("Run All", AllIcons.Actions.Execute), __ -> {
      Executor executor = DefaultRunExecutor.getRunExecutorInstance();
      ConfigurationContext context = ConfigurationContext.getFromContext(e.getDataContext());
      //first producer would be picked up, testng will be ignored completely!
      //all tests would be retrieved again by provider
      ConfigurationFromContext configuration =
        RunConfigurationProducer.getInstance(TestDiscoveryConfigurationProducer.class).findOrCreateConfigurationFromContext(context);
      if (configuration != null) {
        ExecutionUtil.runConfiguration(configuration.getConfigurationSettings(), executor);
      }
    });

    Ref<JBPopup> ref = new Ref<>();
    InplaceButton pinButton = new InplaceButton(
      new IconButton("Pin", AllIcons.General.AutohideOff, AllIcons.General.AutohideOffPressed, AllIcons.General.AutohideOffInactive),
      __ -> {
        PsiElement[] elements = model.getItems().toArray(PsiElement.EMPTY_ARRAY);
        FindUtil.showInUsageView(null, elements, initTitle, project);
        JBPopup pinPopup = ref.get();
        if (pinPopup != null) {
          pinPopup.cancel();
        }
      });

    CompositeActiveComponent component = new CompositeActiveComponent(runButton, pinButton);

    final PopupChooserBuilder builder =
      new PopupChooserBuilder(list)
        .setTitle(initTitle)
        .setMovable(true)
        .setResizable(true)
        .setCommandButton(component)
        .setItemChoosenCallback(() -> PsiNavigateUtil.navigate(list.getSelectedValue()))
        .setMinSize(new JBDimension(500, 300));

    renderer.installSpeedSearch(builder, true);

    JBPopup popup = builder.createPopup();
    ref.set(popup);

    list.setEmptyText("No tests captured for " + methodPresentationName);
    list.setPaintBusy(true);

    popup.showInBestPositionFor(editor);

    JavaPsiFacade javaFacade = JavaPsiFacade.getInstance(project);
    GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
    list.setCellRenderer(renderer);

    ListBackgroundUpdaterTask loadTestsTask = new ListBackgroundUpdaterTask(project, "Load tests", renderer.getComparator()) {
      @Override
      public String getCaption(int size) {
        return "Found " + size + " Tests for " + methodPresentationName;
      }
    };

    loadTestsTask.init((AbstractPopup)popup, list, new Ref<>());

    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      Map<String, String> map = fetchDataFromDiscoveryServer(fqn, methodName);
      map.forEach((classFqn, testMethodName) -> {
        PsiMethod psiMethod = ReadAction.compute(() -> {
          PsiClass cc = classFqn == null ? null : javaFacade.findClass(classFqn, scope);
          return cc == null ? null : ArrayUtil.getFirstElement(cc.findMethodsByName(testMethodName, false));
        });
        if (psiMethod != null) {
          loadTestsTask.updateComponent(psiMethod);
        }
      });

      EdtInvocationManager.getInstance().invokeLater(() -> {
        popup.pack(true, true);
        list.setPaintBusy(false);
      });
    });
  }

  private static final String INTELLIJ_TEST_DISCOVERY_HOST = "http://intellij-test-discovery";

  private static Map<String, String> fetchDataFromDiscoveryServer(@NotNull String classFQName, @NotNull String methodName) {
    String methodFqn = classFQName + "." + methodName;
    RequestBuilder r = HttpRequests.request(INTELLIJ_TEST_DISCOVERY_HOST + "/search/tests/by-method/" + methodFqn);

    try {
      return r.connect(request -> {
        Map<String, String> map = ContainerUtil.newLinkedHashMap();
        ObjectMapper mapper = new ObjectMapper();
        TestsSearchResult result = mapper.readValue(request.getInputStream(), TestsSearchResult.class);

        result.getTests().forEach(s -> {

          s = s.length() > 1 && s.charAt(0) == 'j' ? s.substring(1) : s;
          String classFqn = StringUtil.substringBefore(s, "-");
          String testMethodName = StringUtil.substringAfter(s, "-");

          map.put(classFqn, testMethodName);
        });
        return map;
      });
    }
    catch (HttpRequests.HttpStatusException http) {
      LOG.debug("No tests found for " + methodFqn);
    }
    catch (IOException e) {
      LOG.debug(e);
    }
    return Collections.emptyMap();
  }

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  public static class TestsSearchResult {
    @Nullable
    private String method;

    @SerializedName("class")
    @JsonProperty("class")
    @Nullable
    private String className;

    private int found;

    @NotNull
    private List<String> tests = new ArrayList<>();

    @Nullable
    private String message;

    @Nullable
    public String getMethod() {
      return method;
    }

    public TestsSearchResult setMethod(String method) {
      this.method = method;
      return this;
    }

    @Nullable
    public String getClassName() {
      return className;
    }

    public TestsSearchResult setClassName(String name) {
      this.className = name;
      return this;
    }

    public int getFound() {
      return found;
    }

    @NotNull
    public List<String> getTests() {
      return tests;
    }

    public TestsSearchResult setTests(List<String> tests) {
      this.tests = tests;
      this.found = tests.size();
      return this;
    }

    @Nullable
    public String getMessage() {
      return message;
    }

    public TestsSearchResult setMessage(String message) {
      this.message = message;
      return this;
    }
  }
}
