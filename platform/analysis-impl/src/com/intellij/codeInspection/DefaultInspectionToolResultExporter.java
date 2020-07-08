// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.reference.RefVisitor;
import com.intellij.codeInspection.ui.AggregateResultsExporter;
import com.intellij.codeInspection.ui.GlobalReportedProblemFilter;
import com.intellij.codeInspection.ui.ReportedProblemFilter;
import com.intellij.codeInspection.ui.util.SynchronizedBidiMultiMap;
import com.intellij.configurationStore.JbXmlOutputter;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ArrayFactory;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jdom.Verifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DefaultInspectionToolResultExporter implements InspectionToolResultExporter {
  protected static final Logger LOG = Logger.getInstance(DefaultInspectionToolResultExporter.class);

  public static final String INSPECTION_RESULTS_PROBLEM_CLASS_ELEMENT = "problem_class";
  public static final String INSPECTION_RESULTS_SEVERITY_ATTRIBUTE = "severity";
  public static final String INSPECTION_RESULTS_ATTRIBUTE_KEY_ATTRIBUTE = "attribute_key";
  public static final String INSPECTION_RESULTS_ID_ATTRIBUTE = "id";
  public static final String INSPECTION_RESULTS_DESCRIPTION_ELEMENT = "description";
  public static final String INSPECTION_RESULTS_HINTS_ELEMENT = "hints";
  public static final String INSPECTION_RESULTS_HINT_ELEMENT = "hint";
  public static final String INSPECTION_RESULTS_VALUE_ATTRIBUTE = "value";
  @NotNull protected final InspectionToolWrapper myToolWrapper;
  @NotNull protected final GlobalInspectionContextEx myContext;

  private final SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> myExcludedElements = createBidiMap();

  protected final SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor>
    myProblemElements = createBidiMap();
  protected final SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> mySuppressedElements = createBidiMap();
  protected final SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> myResolvedElements = createBidiMap();

  protected final Map<String, Set<RefEntity>> myContents = Collections.synchronizedMap(new HashMap<>(1)); // keys can be null

  public DefaultInspectionToolResultExporter(@NotNull InspectionToolWrapper toolWrapper, @NotNull GlobalInspectionContextEx context) {
    myToolWrapper = toolWrapper;
    myContext = context;
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

  @Override
  @NotNull
  public SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> getProblemElements() {
    return myProblemElements;
  }

  protected synchronized void writeOutput(CommonProblemDescriptor @NotNull [] descriptions, @NotNull RefEntity refElement) throws
                                                                                                                           IOException {
    Path file = InspectionsResultUtil.getInspectionResultFile(myContext.getOutputPath(), myToolWrapper.getShortName());
    boolean exists = Files.exists(file);
    Files.createDirectories(file.getParent());
    try (Writer writer = Files.newBufferedWriter(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
      if (!exists) {
        writer.write('<');
        writer.write(GlobalInspectionContextBase.PROBLEMS_TAG_NAME);
        writer.write(' ');
        writer.write(GlobalInspectionContextBase.LOCAL_TOOL_ATTRIBUTE);
        writer.write('=');
        writer.write('"');
        writer.write(Boolean.toString(myToolWrapper instanceof LocalInspectionToolWrapper));
        writer.write('"');
        writer.write('>');
        writer.write('\n');
      }

      exportResults(descriptions, refElement, p -> {
        try {
          JbXmlOutputter.collapseMacrosAndWrite(p, getContext().getProject(), writer);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }, d -> false);

      writer.write('\n');
    }
  }

  @Override
  public void exportResults(@NotNull final Consumer<? super Element> resultConsumer,
                            @NotNull final Predicate<? super RefEntity> excludedEntities,
                            @NotNull final Predicate<? super CommonProblemDescriptor> excludedDescriptors) {
    getRefManager().iterate(new RefVisitor() {
      @Override
      public void visitElement(@NotNull RefEntity elem) {
        if (!excludedEntities.test(elem)) {
          exportResults(resultConsumer, elem, excludedDescriptors);
        }
      }
    });
  }

  @Override
  public @NotNull Project getProject() {
    return myContext.getProject();
  }

  @Override
  public void exportResults(@NotNull Consumer<? super Element> resultConsumer,
                            @NotNull RefEntity refEntity,
                            @NotNull Predicate<? super CommonProblemDescriptor> isDescriptorExcluded) {
    CommonProblemDescriptor[] descriptions = getProblemElements().get(refEntity);
    if (descriptions != null) {
      exportResults(descriptions, refEntity, resultConsumer, isDescriptorExcluded);
    }
  }

  protected void exportResults(final CommonProblemDescriptor @NotNull [] descriptors,
                               @NotNull RefEntity refEntity,
                               @NotNull Consumer<? super Element> problemSink,
                               @NotNull Predicate<? super CommonProblemDescriptor> isDescriptorExcluded) {
    for (CommonProblemDescriptor descriptor : descriptors) {
      if (isDescriptorExcluded.test(descriptor)) continue;
      int line = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getLineNumber() : -1;
      Element element = null;
      try {
        element = refEntity.getRefManager().export(refEntity, line);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
      if (element == null) return;
      exportResult(refEntity, descriptor, element);
      problemSink.accept(element);
    }
  }

  private void exportResult(@NotNull RefEntity refEntity, @NotNull CommonProblemDescriptor descriptor, @NotNull Element element) {
    final PsiElement psiElement = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;
    try {
      @NonNls Element problemClassElement = new Element(INSPECTION_RESULTS_PROBLEM_CLASS_ELEMENT);
      problemClassElement.setAttribute(INSPECTION_RESULTS_ID_ATTRIBUTE, myToolWrapper.getShortName());
      problemClassElement.addContent(sanitizeIllegalXmlChars(myToolWrapper.getDisplayName()));

      final HighlightSeverity severity = InspectionToolResultExporter.getSeverity(refEntity, psiElement, this);

      SeverityRegistrar severityRegistrar = myContext.getCurrentProfile().getProfileManager().getSeverityRegistrar();
      HighlightInfoType type = descriptor instanceof ProblemDescriptor
                               ? ProblemDescriptorUtil
                                 .highlightTypeFromDescriptor((ProblemDescriptor)descriptor, severity, severityRegistrar)
                               : ProblemDescriptorUtil
                                 .getHighlightInfoType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING, severity, severityRegistrar);
      problemClassElement.setAttribute(INSPECTION_RESULTS_SEVERITY_ATTRIBUTE, type.getSeverity(psiElement).getName());
      problemClassElement.setAttribute(INSPECTION_RESULTS_ATTRIBUTE_KEY_ATTRIBUTE, type.getAttributesKey().getExternalName());

      element.addContent(problemClassElement);
      if (myToolWrapper instanceof GlobalInspectionToolWrapper) {
        final GlobalInspectionTool globalInspectionTool = ((GlobalInspectionToolWrapper)myToolWrapper).getTool();
        final QuickFix[] fixes = descriptor.getFixes();
        if (fixes != null) {
          @NonNls Element hintsElement = new Element(INSPECTION_RESULTS_HINTS_ELEMENT);
          for (QuickFix fix : fixes) {
            final String hint = globalInspectionTool.getHint(fix);
            if (hint != null) {
              @NonNls Element hintElement = new Element(INSPECTION_RESULTS_HINT_ELEMENT);
              hintElement.setAttribute(INSPECTION_RESULTS_VALUE_ATTRIBUTE, sanitizeIllegalXmlChars(hint));
              hintsElement.addContent(hintElement);
            }
          }
          element.addContent(hintsElement);
        }
      }
      @NonNls final String template = descriptor.getDescriptionTemplate();
      String highlightedText = ProblemDescriptorUtil.extractHighlightedText(descriptor, psiElement);
      @NonNls String problemText = StringUtil
        .replace(StringUtil.replace(template, "#ref", psiElement != null ? highlightedText : ""), " #loc ", " ");
      Element descriptionElement = new Element(INSPECTION_RESULTS_DESCRIPTION_ELEMENT);
      descriptionElement.addContent(sanitizeIllegalXmlChars(problemText));
      element.addContent(descriptionElement);

      Element highLightedElement = new Element("highlighted_element");
      highLightedElement.addContent(sanitizeIllegalXmlChars(highlightedText));
      element.addContent(highLightedElement);

      if (descriptor instanceof ProblemDescriptorBase) {
        TextRange textRange = ((ProblemDescriptorBase)descriptor).getTextRangeForNavigation();
        if (textRange != null) {
          int offset = textRange.getStartOffset() - ((ProblemDescriptorBase)descriptor).getLineStartOffset();
          int length = textRange.getLength();
          element.addContent(new Element("offset").addContent(String.valueOf(offset)));
          element.addContent(new Element("length").addContent(String.valueOf(length)));
        }
      }
    }
    catch (RuntimeException e) {
      String message = "Cannot save results for " + refEntity.getName() + ", inspection which caused problem: " +
                       myToolWrapper.getShortName() + ", problem descriptor " + descriptor;
      if (psiElement != null) {
        message += ", element class: " + psiElement.getClass() + ", containing file: " + psiElement.getContainingFile();
      }
      LOG.error(message, e);
    }
  }

  private static String sanitizeIllegalXmlChars(String text) {
    if (Verifier.checkCharacterData(text) == null) return text;
    return text.codePoints().map(cp -> Verifier.isXMLCharacter(cp) ? cp : '?')
      .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
  }

  protected String getSeverityDelegateName() {
    return getToolWrapper().getShortName();
  }

  @NotNull
  public GlobalInspectionContextEx getContext() {
    return myContext;
  }


  private static @NotNull SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> createBidiMap() {
    return new SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor>() {
      @NotNull
      @Override
      protected ArrayFactory<CommonProblemDescriptor> arrayFactory() {
        return CommonProblemDescriptor.ARRAY_FACTORY;
      }
    };
  }

  @Nullable
  @Override
  public HighlightSeverity getSeverity(@NotNull RefElement element) {
    return ReadAction.nonBlocking(() -> {
      final PsiElement psiElement = ((RefElement)element.getRefManager().getRefinedElement(element)).getPointer().getContainingFile();
      if (psiElement != null) {
        final GlobalInspectionContextBase context = getContext();
        final String shortName = getSeverityDelegateName();
        final Tools tools = context.getTools().get(shortName);
        if (tools != null) {
          for (ScopeToolState state : tools.getTools()) {
            InspectionToolWrapper<?, ?> toolWrapper = state.getTool();
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
    }).executeSynchronously();
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
    RefEntity entity = ObjectUtils.chooseNotNull(myProblemElements.getKeyFor(descriptor), myResolvedElements.getKeyFor(descriptor));
    if (entity != null) {
      myExcludedElements.put(entity, descriptor);
    }
  }

  @Override
  public void suppressProblem(@NotNull CommonProblemDescriptor descriptor) {
    RefEntity removed = myProblemElements.removeValue(descriptor);
    if (removed != null) {
      mySuppressedElements.put(removed, descriptor);
    }
  }

  @Override
  public void suppressProblem(@NotNull RefEntity entity) {
    CommonProblemDescriptor[] removed = myProblemElements.remove(entity);
    if (removed != null) {
      mySuppressedElements.put(entity, removed);
    }
  }

  @Override
  public synchronized void updateContent() {
    myContents.clear();
    updateProblemElements();
  }

  protected void updateProblemElements() {
    final Set<RefEntity> elements;
    if (filterResolvedItems()) {
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
      String groupName =
        element instanceof RefElement ? element.getRefManager().getGroupName((RefElement)element) : element.getQualifiedName();
      registerContentEntry(element, groupName);
    }
  }

  protected boolean filterResolvedItems() {
    return false;
  }

  protected void registerContentEntry(RefEntity element, String packageName) {
    GlobalReportedProblemFilter globalReportedProblemFilter = myContext.getGlobalReportedProblemFilter();
    if (globalReportedProblemFilter == null || globalReportedProblemFilter.shouldReportProblem(element, getToolWrapper().getShortName())) {
      Set<RefEntity> content = myContents.computeIfAbsent(packageName, k -> new HashSet<>());
      content.add(element);
    }
  }

  @Override
  public void addProblemElement(@Nullable RefEntity refElement, CommonProblemDescriptor @NotNull ... descriptions) {
    addProblemElement(refElement, true, descriptions);
  }

  @Override
  public void addProblemElement(@Nullable RefEntity refElement,
                                boolean filterSuppressed,
                                final CommonProblemDescriptor @NotNull ... descriptors) {
    if (refElement == null || descriptors.length == 0) {
      return;
    }

    ReportedProblemFilter filter = myContext.getReportedProblemFilter();
    if (filter != null && !filter.shouldReportProblem(refElement, descriptors)) {
      return;
    }

    checkFromSameFile(refElement, descriptors);
    if (filterSuppressed) {
      if (myContext.getOutputPath() == null || !(myToolWrapper instanceof LocalInspectionToolWrapper)
          || this instanceof AggregateResultsExporter) {
        myProblemElements.put(refElement, descriptors);
      }
      else {
        try {
          writeOutput(descriptors, refElement);
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
    else {
      myProblemElements.put(refElement, descriptors);
    }
  }

  private static void checkFromSameFile(RefEntity element, CommonProblemDescriptor[] descriptors) {
    if (!(element instanceof RefElement)) return;
    SmartPsiElementPointer pointer = ((RefElement)element).getPointer();
    if (pointer == null) return;
    VirtualFile entityFile = ensureNotInjectedFile(pointer.getVirtualFile());
    if (entityFile == null) return;
    for (CommonProblemDescriptor d : descriptors) {
      if (d instanceof ProblemDescriptorBase) {
        VirtualFile file = ((ProblemDescriptorBase)d).getContainingFile();
        if (file != null) {
          LOG.assertTrue(ensureNotInjectedFile(file).equals(entityFile),
                         "descriptor and containing entity files should be the same; descriptor: " + d.getDescriptionTemplate());
        }
      }
    }
  }

  @Override
  public RefEntity getElement(@NotNull CommonProblemDescriptor descriptor) {
    return myProblemElements.getKeyFor(descriptor);
  }

  @Contract("null -> null")
  private static VirtualFile ensureNotInjectedFile(VirtualFile file) {
    return file instanceof VirtualFileWindow ? ((VirtualFileWindow)file).getDelegate() : file;
  }

  @Override
  @NotNull
  public Collection<CommonProblemDescriptor> getProblemDescriptors() {
    return myProblemElements.getValues();
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

  @Override
  public CommonProblemDescriptor @NotNull [] getResolvedProblems(@NotNull RefEntity entity) {
    return myResolvedElements.getOrDefault(entity, CommonProblemDescriptor.EMPTY_ARRAY);
  }

  @Override
  public synchronized boolean hasReportedProblems() {
    return !myContents.isEmpty();
  }

  @NotNull
  @Override
  public Map<String, Set<RefEntity>> getContent() {
    return myContents;
  }

}
