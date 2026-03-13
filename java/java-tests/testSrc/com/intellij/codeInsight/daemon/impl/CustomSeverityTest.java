// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.ExplicitTypeCanBeDiamondInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.ApplicationInspectionProfileManager;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.codeInspection.ex.ScopeToolState;
import com.intellij.codeInspection.ex.ToolsImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.colors.pages.GeneralColorsPage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomSeverityTest extends LightDaemonAnalyzerTestCase {
  private static final String EMBEDDINGS_START_INDEXING_ON_PROJECT_OPEN = "intellij.ml.llm.embeddings.start.indexing.on.project.open";
  private static final String EMBEDDINGS_TRIGGER_INDEXING_ON_SEARCH = "intellij.platform.ml.embeddings.trigger.indexing.on.search";

  private String myEmbeddingsStartIndexingOnProjectOpen;
  private String myEmbeddingsTriggerIndexingOnSearch;

  @Override
  protected void setUp() throws Exception {
    myEmbeddingsStartIndexingOnProjectOpen = System.setProperty(EMBEDDINGS_START_INDEXING_ON_PROJECT_OPEN, Boolean.FALSE.toString());
    myEmbeddingsTriggerIndexingOnSearch = System.setProperty(EMBEDDINGS_TRIGGER_INDEXING_ON_SEARCH, Boolean.FALSE.toString());
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      restoreProperty(EMBEDDINGS_START_INDEXING_ON_PROJECT_OPEN, myEmbeddingsStartIndexingOnProjectOpen);
      restoreProperty(EMBEDDINGS_TRIGGER_INDEXING_ON_SEARCH, myEmbeddingsTriggerIndexingOnSearch);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private static void restoreProperty(@NotNull String name, String value) {
    if (value == null) {
      System.clearProperty(name);
    }
    else {
      System.setProperty(name, value);
    }
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new ExplicitTypeCanBeDiamondInspection(),
    };
  }

  public void testCustomSeverityLayerPriority() {
    final Project project = getProject();

    final String severityName = "X";
    final TextAttributesKey attributesKey = TextAttributesKey.createTextAttributesKey(severityName);
    final HighlightSeverity severity = new HighlightSeverity(severityName, 50);
    final var textAttributes = new SeverityRegistrar.SeverityBasedTextAttributes(
      new TextAttributes(null, Color.PINK, null, null, Font.PLAIN),
      new HighlightInfoType.HighlightInfoTypeImpl(severity, attributesKey)
    );
    final SeverityRegistrar registrar = SeverityRegistrar.getSeverityRegistrar(project);
    registrar.registerSeverity(textAttributes, null);
    Disposer.register(getTestRootDisposable(), () -> registrar.unregisterSeverity(severity));

    final InspectionProfileImpl profile = InspectionProfileManager.getInstance(project).getCurrentProfile();
    profile.setErrorLevel(HighlightDisplayKey.find("Convert2Diamond"), HighlightDisplayLevel.find(severity), project);
    assertEquals(severity, profile.getErrorLevel(HighlightDisplayKey.find("Convert2Diamond"), null).getSeverity());

    configureFromFileText("test.java", """
      import java.util.ArrayList;
      import java.util.List;
      
      class Foo {
        public List<String> foo() {
          List<String> list = new ArrayList<String>();
          return list;
        }
      }
      """);
    final List<HighlightInfo> highlighting = ContainerUtil.filter(
      doHighlighting(),
      highlightInfo -> Objects.requireNonNullElse(highlightInfo.getDescription(), "").startsWith("Explicit type argument ")
    );
    assertSize(1, highlighting);

    final HighlightInfo info = highlighting.getFirst();
    assertEquals(severity, info.getSeverity());
    assertEquals(HighlighterLayer.WARNING, info.getHighlighter().getLayer());
  }

  public void testProvidedSeverityAddedAfterProfileManagerInit() {
    ApplicationInspectionProfileManager.getInstanceImpl().forceInitProfilesInTestUntil(getTestRootDisposable());
    SeverityRegistrar registrar = SeverityRegistrar.getSeverityRegistrar(getProject());

    HighlightInfoType.HighlightInfoTypeImpl infoType =
      infoType("CUSTOM_DYNAMIC", 350, TextAttributesKey.createTextAttributesKey("CUSTOM_DYNAMIC_KEY"));
    registerProvider(new TestSeveritiesProvider(infoType), getTestRootDisposable());

    HighlightSeverity severity = Objects.requireNonNull(registrar.getSeverity("CUSTOM_DYNAMIC"));
    assertEquals(infoType.getAttributesKey(), registrar.getHighlightInfoTypeBySeverity(severity).getAttributesKey());
    assertSame(Objects.requireNonNull(HighlightDisplayLevel.find("CUSTOM_DYNAMIC")), HighlightDisplayLevel.find(severity));

    GeneralColorsPage page = new GeneralColorsPage();
    assertTrue(page.getDemoText().contains("<CUSTOM_DYNAMIC>custom_dynamic</CUSTOM_DYNAMIC>"));
    assertEquals(infoType.getAttributesKey(),
                 Objects.requireNonNull(page.getAdditionalHighlightingTagToDescriptorMap()).get("CUSTOM_DYNAMIC"));
  }

  public void testProvidedSeverityRemovalRewritesLoadedProjectProfileState() {
    ApplicationInspectionProfileManager.getInstanceImpl().forceInitProfilesInTestUntil(getTestRootDisposable());
    SeverityRegistrar registrar = SeverityRegistrar.getSeverityRegistrar(getProject());

    HighlightInfoType.HighlightInfoTypeImpl infoType =
      infoType("REMOVABLE_DYNAMIC", 360, TextAttributesKey.createTextAttributesKey("REMOVABLE_DYNAMIC_KEY"));
    Disposable disposable = registerProvider(new TestSeveritiesProvider(infoType));

    HighlightSeverity severity = Objects.requireNonNull(registrar.getSeverity("REMOVABLE_DYNAMIC"));
    HighlightDisplayLevel level = Objects.requireNonNull(HighlightDisplayLevel.find(severity));
    InspectionProfileImpl profile = InspectionProfileManager.getInstance(getProject()).getCurrentProfile();
    HighlightDisplayKey key = Objects.requireNonNull(HighlightDisplayKey.find("Convert2Diamond"));
    ToolsImpl tools = profile.getTools("Convert2Diamond", getProject());
    NamedScope scope = new NamedScope("REMOVABLE_DYNAMIC_SCOPE", null);
    ScopeToolState scopedState = tools.addTool(scope, tools.getDefaultState().getTool().createCopy(), true, level);
    profile.setErrorLevel(key, level, getProject());
    assertSame(level, tools.getDefaultState().getLevel());
    assertSame(level, scopedState.getLevel());
    assertSame(level, profile.getErrorLevel(key, scope, getProject()));

    Disposer.dispose(disposable);

    assertNull(registrar.getSeverity("REMOVABLE_DYNAMIC"));
    assertNull(HighlightDisplayLevel.find("REMOVABLE_DYNAMIC"));
    assertSame(HighlightDisplayLevel.WARNING, tools.getDefaultState().getLevel());
    assertSame(HighlightDisplayLevel.WARNING, scopedState.getLevel());
    assertSame(HighlightDisplayLevel.WARNING, profile.getErrorLevel(key, scope, getProject()));
    assertSame(HighlightDisplayLevel.WARNING, profile.getErrorLevel(key, null));
  }

  public void testProvidedSeverityRemovalRewritesLoadedApplicationProfileState() {
    ApplicationInspectionProfileManager applicationManager = ApplicationInspectionProfileManager.getInstanceImpl();
    applicationManager.forceInitProfilesInTestUntil(getTestRootDisposable());

    HighlightInfoType.HighlightInfoTypeImpl infoType =
      infoType("APPLICATION_DYNAMIC", 365, TextAttributesKey.createTextAttributesKey("APPLICATION_DYNAMIC_KEY"));
    Disposable disposable = registerProvider(new TestSeveritiesProvider(infoType));

    InspectionProfileImpl applicationProfile = createApplicationProfile(applicationManager, getTestName(false) + "ApplicationProfile");
    ProjectInspectionProfileManager projectManager = ProjectInspectionProfileManager.getInstance(getProject());
    projectManager.useApplicationProfile(applicationProfile.getName());
    assertSame(applicationProfile, projectManager.getCurrentProfile());

    boolean initInspections = InspectionProfileImpl.INIT_INSPECTIONS;
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    Disposer.register(getTestRootDisposable(), () -> InspectionProfileImpl.INIT_INSPECTIONS = initInspections);
    applicationProfile.initInspectionTools(getProject());
    assertNotEmpty(applicationProfile.getAllTools());
    ScopeToolState appToolState = applicationProfile.getAllTools().getFirst();
    String shortName = appToolState.getTool().getShortName();
    HighlightSeverity severity = Objects.requireNonNull(applicationManager.getSeverityRegistrar().getSeverity("APPLICATION_DYNAMIC"));
    HighlightDisplayLevel level = Objects.requireNonNull(HighlightDisplayLevel.find(severity));
    HighlightDisplayKey key = Objects.requireNonNull(HighlightDisplayKey.find(shortName));
    applicationProfile.setErrorLevel(key, level, getProject());
    assertSame(level, applicationProfile.getTools(shortName, getProject()).getDefaultState().getLevel());

    Disposer.dispose(disposable);

    assertSame(HighlightDisplayLevel.WARNING, applicationProfile.getTools(shortName, getProject()).getDefaultState().getLevel());
    assertSame(HighlightDisplayLevel.WARNING, applicationProfile.getErrorLevel(key, null));
  }

  public void testProvidedSeverityRemovalPreservesSameNamedCustomSeverity() {
    ApplicationInspectionProfileManager.getInstanceImpl().forceInitProfilesInTestUntil(getTestRootDisposable());
    InspectionProfileImpl profile = InspectionProfileManager.getInstance(getProject()).getCurrentProfile();
    SeverityRegistrar registrar = profile.getProfileManager().getSeverityRegistrar();

    HighlightSeverity customSeverity = new HighlightSeverity("SHADOWED_DYNAMIC", 305);
    TextAttributesKey customKey = TextAttributesKey.createTextAttributesKey("SHADOWED_DYNAMIC_CUSTOM_KEY");
    registrar.registerSeverity(new SeverityRegistrar.SeverityBasedTextAttributes(
      new TextAttributes(null, Color.CYAN, null, null, Font.PLAIN),
      new HighlightInfoType.HighlightInfoTypeImpl(customSeverity, customKey)
    ), null);
    Disposer.register(getTestRootDisposable(), () -> registrar.unregisterSeverity(customSeverity));

    HighlightDisplayKey key = Objects.requireNonNull(HighlightDisplayKey.find("Convert2Diamond"));
    HighlightDisplayLevel customLevel = Objects.requireNonNull(HighlightDisplayLevel.find(customSeverity));
    profile.setErrorLevel(key, customLevel, getProject());
    assertEquals(customSeverity, profile.getErrorLevel(key, null).getSeverity());

    TextAttributesKey providerKey = TextAttributesKey.createTextAttributesKey("SHADOWED_DYNAMIC_PROVIDER_KEY");
    Disposable disposable = registerProvider(new TestSeveritiesProvider(infoType("SHADOWED_DYNAMIC", 360, providerKey)));
    assertEquals(360, Objects.requireNonNull(registrar.getSeverity("SHADOWED_DYNAMIC")).myVal);

    Disposer.dispose(disposable);

    HighlightSeverity restoredSeverity = Objects.requireNonNull(registrar.getSeverity("SHADOWED_DYNAMIC"));
    assertEquals(customSeverity, restoredSeverity);
    assertEquals(customKey, registrar.getHighlightInfoTypeBySeverity(restoredSeverity).getAttributesKey());
    assertEquals(customSeverity, Objects.requireNonNull(HighlightDisplayLevel.find("SHADOWED_DYNAMIC")).getSeverity());
    assertEquals(customSeverity, profile.getErrorLevel(key, null).getSeverity());
  }

  public void testProvidedSeverityDuplicateNameFallsBackToEarlierProvider() {
    ApplicationInspectionProfileManager.getInstanceImpl().forceInitProfilesInTestUntil(getTestRootDisposable());
    SeverityRegistrar registrar = SeverityRegistrar.getSeverityRegistrar(getProject());

    TextAttributesKey firstKey = TextAttributesKey.createTextAttributesKey("DUPLICATE_DYNAMIC_FIRST");
    TextAttributesKey secondKey = TextAttributesKey.createTextAttributesKey("DUPLICATE_DYNAMIC_SECOND");
    Disposable firstProvider = registerProvider(new TestSeveritiesProvider(duplicateDynamicInfoType(
      310,
      firstKey,
      new AtomicInteger(),
      new TestIcon(12, 12)
    )));
    HighlightDisplayLevel initialLevel = Objects.requireNonNull(HighlightDisplayLevel.find("DUPLICATE_DYNAMIC"));
    assertEquals(310, initialLevel.getSeverity().myVal);
    assertEquals(firstKey, registrar.getHighlightInfoTypeBySeverity(Objects.requireNonNull(registrar.getSeverity("DUPLICATE_DYNAMIC")))
      .getAttributesKey());
    assertTrue(findDuplicateDynamicStandardSeverityType() instanceof HighlightInfoType.Iconable);

    Disposable secondProvider = registerProvider(new TestSeveritiesProvider(duplicateDynamicInfoType(
      320,
      secondKey,
      new AtomicInteger(),
      new TestIcon(14, 14)
    )));
    assertSame(initialLevel, HighlightDisplayLevel.find("DUPLICATE_DYNAMIC"));
    assertEquals(320, initialLevel.getSeverity().myVal);
    assertEquals(320, Objects.requireNonNull(registrar.getSeverity("DUPLICATE_DYNAMIC")).myVal);
    assertEquals(secondKey, registrar.getHighlightInfoTypeBySeverity(Objects.requireNonNull(registrar.getSeverity("DUPLICATE_DYNAMIC")))
      .getAttributesKey());
    assertTrue(findDuplicateDynamicStandardSeverityType() instanceof HighlightInfoType.Iconable);

    Disposer.dispose(secondProvider);

    assertSame(initialLevel, HighlightDisplayLevel.find("DUPLICATE_DYNAMIC"));
    assertEquals(310, initialLevel.getSeverity().myVal);
    assertEquals(310, Objects.requireNonNull(registrar.getSeverity("DUPLICATE_DYNAMIC")).myVal);
    assertEquals(firstKey, registrar.getHighlightInfoTypeBySeverity(Objects.requireNonNull(registrar.getSeverity("DUPLICATE_DYNAMIC")))
      .getAttributesKey());
    assertTrue(findDuplicateDynamicStandardSeverityType() instanceof HighlightInfoType.Iconable);
    Disposer.dispose(firstProvider);
  }

  public void testProvidedSeverityChangePublishesProjectSeverityEvents() {
    ApplicationInspectionProfileManager.getInstanceImpl().forceInitProfilesInTestUntil(getTestRootDisposable());
    SeverityRegistrar registrar = SeverityRegistrar.getSeverityRegistrar(getProject());

    AtomicInteger notifications = new AtomicInteger();
    getProject().getMessageBus().connect(getTestRootDisposable())
      .subscribe(SeverityRegistrar.SEVERITIES_CHANGED_TOPIC, notifications::incrementAndGet);

    long beforeAdd = registrar.getModificationCount();
    Disposable disposable = registerProvider(
      new TestSeveritiesProvider(infoType("NOTIFY_DYNAMIC", 370, TextAttributesKey.createTextAttributesKey("NOTIFY_DYNAMIC_KEY"))));
    assertTrue(registrar.getModificationCount() > beforeAdd);
    assertEquals(1, notifications.get());

    long beforeRemove = registrar.getModificationCount();
    Disposer.dispose(disposable);
    assertTrue(registrar.getModificationCount() > beforeRemove);
    assertEquals(2, notifications.get());
  }

  private static HighlightInfoType.HighlightInfoTypeImpl infoType(@NotNull String name, int value, @NotNull TextAttributesKey key) {
    return new HighlightInfoType.HighlightInfoTypeImpl(new HighlightSeverity(name, value), key);
  }

  private static CountingIconableInfoType duplicateDynamicInfoType(int value,
                                                                   @NotNull TextAttributesKey key,
                                                                   @NotNull AtomicInteger iconRequests,
                                                                   @NotNull Icon icon) {
    return new CountingIconableInfoType(new HighlightSeverity("DUPLICATE_DYNAMIC", value), key, iconRequests, icon);
  }

  private static @NotNull HighlightInfoType findDuplicateDynamicStandardSeverityType() {
    return SeverityRegistrar.standardSeverities().stream()
      .filter(type -> "DUPLICATE_DYNAMIC".equals(type.getSeverity(null).getName()))
      .findFirst()
      .orElseThrow();
  }

  private static void registerProvider(@NotNull SeveritiesProvider provider, @NotNull Disposable disposable) {
    SeveritiesProvider.EP_NAME.getPoint().registerExtension(provider, disposable);
  }

  private @NotNull Disposable registerProvider(@NotNull SeveritiesProvider provider) {
    Disposable disposable = Disposer.newDisposable();
    registerProvider(provider, disposable);
    Disposer.register(getTestRootDisposable(), disposable);
    return disposable;
  }

  private InspectionProfileImpl createApplicationProfile(@NotNull ApplicationInspectionProfileManager profileManager,
                                                         @NotNull String profileName) {
    InspectionProfileImpl profile =
      new InspectionProfileImpl(profileName, InspectionToolRegistrar.getInstance(), profileManager,
                                profileManager.getCurrentProfile(), null);
    profileManager.addProfile(profile);
    Disposer.register(getTestRootDisposable(), () -> profileManager.deleteProfile(profileName));
    return profile;
  }

  private static final class TestSeveritiesProvider extends SeveritiesProvider {
    private final @NotNull List<@NotNull HighlightInfoType> myHighlightInfoTypes;

    private TestSeveritiesProvider(HighlightInfoType... highlightInfoTypes) {
      myHighlightInfoTypes = List.of(highlightInfoTypes);
    }

    @Override
    public @NotNull List<@NotNull HighlightInfoType> getSeveritiesHighlightInfoTypes() {
      return myHighlightInfoTypes;
    }
  }

  private static final class CountingIconableInfoType extends HighlightInfoType.HighlightInfoTypeImpl implements HighlightInfoType.Iconable {
    private final @NotNull AtomicInteger myIconRequests;
    private final @NotNull Icon myIcon;

    private CountingIconableInfoType(@NotNull HighlightSeverity severity,
                                     @NotNull TextAttributesKey attributesKey,
                                     @NotNull AtomicInteger iconRequests,
                                     @NotNull Icon icon) {
      super(severity, attributesKey);
      myIconRequests = iconRequests;
      myIcon = icon;
    }

    @Override
    public @NotNull Icon getIcon() {
      myIconRequests.incrementAndGet();
      return myIcon;
    }
  }

  private static final class TestIcon implements Icon {
    private final int myWidth;
    private final int myHeight;

    private TestIcon(int width, int height) {
      myWidth = width;
      myHeight = height;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
    }

    @Override
    public int getIconWidth() {
      return myWidth;
    }

    @Override
    public int getIconHeight() {
      return myHeight;
    }
  }
}
