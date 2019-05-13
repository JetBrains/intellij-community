// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.codeInspection.ui.actions.ExportHTMLAction;
import com.intellij.codeInspection.ui.util.SynchronizedBidiMultiMap;
import com.intellij.configurationStore.JbXmlOutputter;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ArrayFactory;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DefaultInspectionToolPresentation implements InspectionToolPresentation {
  protected static final Logger LOG = Logger.getInstance(DefaultInspectionToolPresentation.class);

  @NotNull private final InspectionToolWrapper myToolWrapper;
  @NotNull protected final GlobalInspectionContextImpl myContext;

  private final SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> myProblemElements = createBidiMap();
  private final SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> mySuppressedElements = createBidiMap();
  private final SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> myResolvedElements = createBidiMap();
  private final SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> myExcludedElements = createBidiMap();

  protected final Map<String, Set<RefEntity>> myContents = Collections.synchronizedMap(new HashMap<>(1)); // keys can be null

  private DescriptorComposer myComposer;
  private volatile boolean isDisposed;

  public DefaultInspectionToolPresentation(@NotNull InspectionToolWrapper toolWrapper, @NotNull GlobalInspectionContextImpl context) {
    myToolWrapper = toolWrapper;
    myContext = context;
  }

  @Override
  public void resolveProblem(@NotNull CommonProblemDescriptor descriptor) {
    RefEntity entity = myProblemElements.removeValue(descriptor);
    if (entity != null) {
      myResolvedElements.put(entity, descriptor);
    }
  }

  @Override
  public boolean isProblemResolved(@Nullable CommonProblemDescriptor descriptor) {
    return myResolvedElements.containsValue(descriptor);
  }

  @Override
  public boolean isProblemResolved(@Nullable RefEntity entity) {
    return myResolvedElements.containsKey(entity) && !myProblemElements.containsKey(entity);
  }

  @NotNull
  @Override
  public Collection<RefEntity> getResolvedElements() {
    return myResolvedElements.keys();
  }

  @NotNull
  @Override
  public CommonProblemDescriptor[] getResolvedProblems(@NotNull RefEntity entity) {
    return myResolvedElements.getOrDefault(entity, CommonProblemDescriptor.EMPTY_ARRAY);
  }

  @Override
  public void suppressProblem(@NotNull CommonProblemDescriptor descriptor) {
    mySuppressedElements.put(myProblemElements.removeValue(descriptor), descriptor);
  }

  @Override
  public void suppressProblem(@NotNull RefEntity entity) {
    CommonProblemDescriptor[] removed = myProblemElements.remove(entity);
    if (removed != null) {
      mySuppressedElements.put(entity, removed);
    }
  }

  @Override
  public boolean isSuppressed(RefEntity element) {
    return mySuppressedElements.containsKey(element);
  }

  @Override
  public boolean isSuppressed(CommonProblemDescriptor descriptor) {
    return mySuppressedElements.containsValue(descriptor);
  }

  @NotNull
  @Override
  public CommonProblemDescriptor[] getSuppressedProblems(@NotNull RefEntity entity) {
    return mySuppressedElements.getOrDefault(entity, CommonProblemDescriptor.EMPTY_ARRAY);
  }

  @Nullable
  @Override
  public HighlightSeverity getSeverity(@NotNull RefElement element) {
    final PsiElement psiElement = ((RefElement)element.getRefManager().getRefinedElement(element)).getPointer().getContainingFile();
    if (psiElement != null) {
      final GlobalInspectionContextImpl context = getContext();
      final String shortName = getSeverityDelegateName();
      final Tools tools = context.getTools().get(shortName);
      if (tools != null) {
        for (ScopeToolState state : tools.getTools()) {
          InspectionToolWrapper toolWrapper = state.getTool();
          if (toolWrapper == getToolWrapper()) {
            return context.getCurrentProfile().getErrorLevel(HighlightDisplayKey.find(shortName), psiElement).getSeverity();
          }
        }
      }

      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(context.getProject()).getCurrentProfile();
      final HighlightDisplayLevel level = profile.getErrorLevel(HighlightDisplayKey.find(shortName), psiElement);
      return level.getSeverity();
    }
    return null;
  }

  @Override
  public boolean isExcluded(@NotNull CommonProblemDescriptor descriptor) {
    return myExcludedElements.containsValue(descriptor);
  }

  @Override
  public boolean isExcluded(@NotNull RefEntity entity) {
    CommonProblemDescriptor[] excluded = myExcludedElements.get(entity);
    CommonProblemDescriptor[] problems = myProblemElements.get(entity);
    return excluded != null && problems != null && Comparing.equal(ContainerUtil.set(excluded), ContainerUtil.set(problems));
  }

  @Override
  public void amnesty(@NotNull RefEntity element) {
    myExcludedElements.remove(element);
  }

  @Override
  public void exclude(@NotNull RefEntity element) {
    myExcludedElements.put(element, myProblemElements.getOrDefault(element, CommonProblemDescriptor.EMPTY_ARRAY));
  }

  @Override
  public void amnesty(@NotNull CommonProblemDescriptor descriptor) {
    myExcludedElements.removeValue(descriptor);
  }

  @Override
  public void exclude(@NotNull CommonProblemDescriptor descriptor) {
    RefEntity entity = ObjectUtils.notNull(myProblemElements.getKeyFor(descriptor), () -> myResolvedElements.getKeyFor(descriptor));
    myExcludedElements.put(entity, descriptor);
  }

  protected String getSeverityDelegateName() {
    return getToolWrapper().getShortName();
  }

  @Override
  @NotNull
  public InspectionToolWrapper getToolWrapper() {
    return myToolWrapper;
  }

  @NotNull
  public RefManager getRefManager() {
    return getContext().getRefManager();
  }

  @NotNull
  @Override
  public GlobalInspectionContextImpl getContext() {
    return myContext;
  }

  @Override
  public void exportResults(@NotNull final Consumer<Element> resultConsumer,
                            @NotNull final Predicate<? super RefEntity> excludedEntities,
                            @NotNull final Predicate<? super CommonProblemDescriptor> excludedDescriptors) {
    getRefManager().iterate(new RefVisitor(){
      @Override
      public void visitElement(@NotNull RefEntity elem) {
        if (!excludedEntities.test(elem)) {
          exportResults(resultConsumer, elem, excludedDescriptors);
        }
      }
    });
  }

  @Override
  public void addProblemElement(@Nullable RefEntity refElement, @NotNull CommonProblemDescriptor... descriptions){
    addProblemElement(refElement, true, descriptions);
  }

  @Override
  public void addProblemElement(@Nullable RefEntity refElement, boolean filterSuppressed, @NotNull final CommonProblemDescriptor... descriptors) {
    if (refElement == null || descriptors.length == 0) return;
    checkFromSameFile(refElement, descriptors);
    if (filterSuppressed) {
      if (myContext.getOutputPath() == null || !(myToolWrapper instanceof LocalInspectionToolWrapper)) {
        myProblemElements.put(refElement, descriptors);
      }
      else {
        writeOutput(descriptors, refElement);
      }
    }
    else {
      myProblemElements.put(refElement, descriptors);
    }

    final GlobalInspectionContextImpl context = getContext();
    if (context.isViewClosed() || !(refElement instanceof RefElement)) {
      return;
    }
    if (myToolWrapper instanceof LocalInspectionToolWrapper && (!ApplicationManager.getApplication().isUnitTestMode() || GlobalInspectionContextImpl.TESTING_VIEW)) {
      context.initializeViewIfNeed().doWhenDone(() -> context.getView().addProblemDescriptors(myToolWrapper, refElement, descriptors));
    }
  }

  protected boolean isDisposed() {
    return isDisposed;
  }

  private synchronized void writeOutput(@NotNull final CommonProblemDescriptor[] descriptions, @NotNull RefEntity refElement) {
    final File file = ExportHTMLAction.getInspectionResultFile(myContext.getOutputPath(), myToolWrapper.getShortName());
    boolean exists = file.exists();
    FileUtil.createParentDirs(file);
    try (PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), CharsetToolkit.UTF8_CHARSET)))) {

      if (!exists) {
        XMLStreamWriter xmlWriter = XMLOutputFactory.newInstance().createXMLStreamWriter(writer);
        xmlWriter.writeStartElement(GlobalInspectionContextBase.PROBLEMS_TAG_NAME);
        xmlWriter.writeAttribute(GlobalInspectionContextBase.LOCAL_TOOL_ATTRIBUTE, Boolean.toString(myToolWrapper instanceof LocalInspectionToolWrapper));
        xmlWriter.writeCharacters("\n");
        xmlWriter.flush();
      }

      exportResults(descriptions, refElement, p -> {
        try {
          JbXmlOutputter.collapseMacrosAndWrite(p, getContext().getProject(), writer);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }, d -> false);

      writer.append("\n");
    }
    catch (XMLStreamException | IOException e) {
      LOG.error(e);
    }
  }

  @Override
  @NotNull
  public Collection<CommonProblemDescriptor> getProblemDescriptors() {
    return myProblemElements.getValues();
  }

  @Override
  public void ignoreElement(@NotNull final RefEntity refEntity) {
    myProblemElements.remove(refEntity);
  }

  @Override
  public void cleanup() {
    isDisposed = true;
  }

  @Override
  @Nullable
  public CommonProblemDescriptor[] getDescriptions(@NotNull RefEntity refEntity) {
    final CommonProblemDescriptor[] problems = getProblemElements().getOrDefault(refEntity, null);
    if (problems == null) return null;

    if (!refEntity.isValid()) {
      ignoreElement(refEntity);
      return null;
    }

    return problems;
  }

  @NotNull
  @Override
  public HTMLComposerImpl getComposer() {
    if (myComposer == null) {
      myComposer = new DescriptorComposer(this);
    }
    return myComposer;
  }

  @Override
  public void exportResults(@NotNull Consumer<Element> resultConsumer,
                            @NotNull RefEntity refEntity,
                            @NotNull Predicate<? super CommonProblemDescriptor> isDescriptorExcluded) {
    CommonProblemDescriptor[] descriptions = getProblemElements().get(refEntity);
    if (descriptions != null) {
      exportResults(descriptions, refEntity, resultConsumer, isDescriptorExcluded);
    }
  }

  private void exportResults(@NotNull final CommonProblemDescriptor[] descriptors,
                             @NotNull RefEntity refEntity,
                             @NotNull Consumer<Element> problemSink,
                             @NotNull Predicate<? super CommonProblemDescriptor> isDescriptorExcluded) {
    for (CommonProblemDescriptor descriptor : descriptors) {
      if (isDescriptorExcluded.test(descriptor)) continue;
      int line = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getLineNumber() : -1;
      Element element = refEntity.getRefManager().export(refEntity, line);
      if (element == null) return;
      exportResult(refEntity, descriptor, element);
      problemSink.accept(element);
    }
  }

  private void exportResult(@NotNull RefEntity refEntity, @NotNull CommonProblemDescriptor descriptor, @NotNull Element element) {
    try {
      final PsiElement psiElement = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;

      @NonNls Element problemClassElement = new Element(InspectionsBundle.message("inspection.export.results.problem.element.tag"));
      problemClassElement.addContent(myToolWrapper.getDisplayName());

      final HighlightSeverity severity = InspectionToolPresentation.getSeverity(refEntity, psiElement, this);

      if (severity != null) {
        SeverityRegistrar severityRegistrar = myContext.getCurrentProfile().getProfileManager().getSeverityRegistrar();
        HighlightInfoType type = descriptor instanceof ProblemDescriptor
                                 ? ProblemDescriptorUtil
                                   .highlightTypeFromDescriptor((ProblemDescriptor)descriptor, severity, severityRegistrar)
                                 : ProblemDescriptorUtil
                                   .getHighlightInfoType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING, severity, severityRegistrar);
        problemClassElement.setAttribute("severity", type.getSeverity(psiElement).getName());
        problemClassElement.setAttribute("attribute_key", type.getAttributesKey().getExternalName());
      }

      element.addContent(problemClassElement);
      if (myToolWrapper instanceof GlobalInspectionToolWrapper) {
        final GlobalInspectionTool globalInspectionTool = ((GlobalInspectionToolWrapper)myToolWrapper).getTool();
        final QuickFix[] fixes = descriptor.getFixes();
        if (fixes != null) {
          @NonNls Element hintsElement = new Element("hints");
          for (QuickFix fix : fixes) {
            final String hint = globalInspectionTool.getHint(fix);
            if (hint != null) {
              @NonNls Element hintElement = new Element("hint");
              hintElement.setAttribute("value", hint);
              hintsElement.addContent(hintElement);
            }
          }
          element.addContent(hintsElement);
        }
      }
      @NonNls final String template = descriptor.getDescriptionTemplate();
      @NonNls String problemText = StringUtil.replace(StringUtil.replace(template, "#ref", psiElement != null ? ProblemDescriptorUtil
        .extractHighlightedText(descriptor, psiElement) : ""), " #loc ", " ");
      Element descriptionElement = new Element(InspectionsBundle.message("inspection.export.results.description.tag"));
      descriptionElement.addContent(problemText);
      element.addContent(descriptionElement);
    }
    catch (RuntimeException e) {
      LOG.info("Cannot save results for " + refEntity.getName() + ", inspection which caused problem: " + myToolWrapper.getShortName() + ", problem descriptor " + descriptor);
    }
  }

  @Override
  public synchronized boolean hasReportedProblems() {
    return !myContents.isEmpty();
  }

  @Override
  public synchronized void updateContent() {
    myContents.clear();
    updateProblemElements();
  }

  protected void updateProblemElements() {
    final Set<RefEntity> elements;
    if (getContext().getUIOptions().FILTER_RESOLVED_ITEMS) {
      // only non-excluded actual problems
      elements = getProblemElements().keys().stream().filter(entity -> !isExcluded(entity)).collect(Collectors.toSet());
    }
    else {
      // add actual problems
      elements = new THashSet<>(getProblemElements().keys());
      // add quick-fixed elements
      elements.addAll(getResolvedElements());
      // add suppressed elements
      elements.addAll(mySuppressedElements.keys());
    }

    for (RefEntity element : elements) {
      String groupName = element instanceof RefElement ? element.getRefManager().getGroupName((RefElement)element) : element.getQualifiedName() ;
      registerContentEntry(element, groupName);
    }
  }

  protected void registerContentEntry(RefEntity element, String packageName) {
    Set<RefEntity> content = myContents.computeIfAbsent(packageName, k -> new HashSet<>());
    content.add(element);
  }

  @NotNull
  @Override
  public Map<String, Set<RefEntity>> getContent() {
    return myContents;
  }

  @Override
  @NotNull
  public QuickFixAction[] getQuickFixes(@NotNull RefEntity... refElements) {
    return QuickFixAction.EMPTY;
  }

  @Override
  public RefEntity getElement(@NotNull CommonProblemDescriptor descriptor) {
    return myProblemElements.getKeyFor(descriptor);
  }

  @Override
  @NotNull
  public SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> getProblemElements() {
    return myProblemElements;
  }

  @Override
  @Nullable
  public IntentionAction findQuickFixes(@NotNull final CommonProblemDescriptor problemDescriptor, final String hint) {
    InspectionProfileEntry tool = getToolWrapper().getTool();
    if (!(tool instanceof GlobalInspectionTool)) return null;
    final QuickFix fix = ((GlobalInspectionTool)tool).getQuickFix(hint);
    if (fix == null) {
      return null;
    }
    if (problemDescriptor instanceof ProblemDescriptor) {
      final ProblemDescriptor descriptor = new ProblemDescriptorImpl(((ProblemDescriptor)problemDescriptor).getStartElement(),
                                                                     ((ProblemDescriptor)problemDescriptor).getEndElement(),
                                                                     problemDescriptor.getDescriptionTemplate(),
                                                                     new LocalQuickFix[]{(LocalQuickFix)fix},
                                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false, null, false);
      return QuickFixWrapper.wrap(descriptor, 0);
    }
    return new IntentionAction() {
      @Override
      @NotNull
      public String getText() {
        return fix.getName();
      }

      @Override
      @NotNull
      public String getFamilyName() {
        return fix.getFamilyName();
      }

      @Override
      public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return true;
      }

      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        //noinspection unchecked
        fix.applyFix(project, problemDescriptor); //todo check type consistency
      }

      @Override
      public boolean startInWriteAction() {
        return true;
      }
    };
  }

  private static SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> createBidiMap() {
    return new SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor>() {
      @NotNull
      @Override
      protected ArrayFactory<CommonProblemDescriptor> arrayFactory() {
        return CommonProblemDescriptor.ARRAY_FACTORY;
      }
    };
  }

  private static void checkFromSameFile(RefEntity element, CommonProblemDescriptor[] descriptors) {
    if (!(element instanceof RefElement)) return;
    SmartPsiElementPointer pointer = ((RefElement)element).getPointer();
    if (pointer == null) return;
    VirtualFile entityFile = ensureNotInjectedFile(pointer.getVirtualFile());
    if (entityFile == null) return;
    StreamEx.of(descriptors).select(ProblemDescriptorBase.class).forEach(d -> {
      VirtualFile file = d.getContainingFile();
      if (file != null) {
        LOG.assertTrue(ensureNotInjectedFile(file).equals(entityFile),
                       "descriptor and containing entity files should be the same; descriptor: " + d.getDescriptionTemplate());
      }
    });
  }

  @Contract("null -> null")
  private static VirtualFile ensureNotInjectedFile(VirtualFile file) {
    return file instanceof VirtualFileWindow ? ((VirtualFileWindow)file).getDelegate() : file;
  }
}
