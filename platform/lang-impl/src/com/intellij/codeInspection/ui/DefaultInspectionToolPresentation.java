/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.*;
import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import gnu.trove.Equality;
import org.jdom.Element;
import org.jdom.IllegalDataException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.function.Predicate;

public class DefaultInspectionToolPresentation implements ProblemDescriptionsProcessor, InspectionToolPresentation {
  protected static final Logger LOG = Logger.getInstance(DefaultInspectionToolPresentation.class);

  @NotNull private final InspectionToolWrapper myToolWrapper;
  @NotNull private final GlobalInspectionContextImpl myContext;
  protected InspectionNode myToolNode;

  private static final Object lock = new Object();
  private final Map<RefEntity, CommonProblemDescriptor[]> myProblemElements =
    ConcurrentCollectionFactory.createMap(ContainerUtil.identityStrategy());
  protected final Map<String, Set<RefEntity>> myContents = Collections.synchronizedMap(new HashMap<String, Set<RefEntity>>(1)); // keys can be null
  private final Set<RefModule> myModulesProblems = Collections.synchronizedSet(ContainerUtil.newIdentityTroveSet());
  private final Map<CommonProblemDescriptor, RefEntity> myProblemToElements = Collections.synchronizedMap(ContainerUtil.newIdentityTroveMap());
  private DescriptorComposer myComposer;
  private final Map<RefEntity, Set<QuickFix>> myQuickFixActions = Collections.synchronizedMap(ContainerUtil.newIdentityTroveMap());
  private final Map<RefEntity, CommonProblemDescriptor[]> myIgnoredElements = Collections.synchronizedMap(ContainerUtil.newIdentityTroveMap());

  private volatile boolean isDisposed;

  public DefaultInspectionToolPresentation(@NotNull InspectionToolWrapper toolWrapper, @NotNull GlobalInspectionContextImpl context) {
    myToolWrapper = toolWrapper;
    myContext = context;
  }

  @Override
  public HighlightSeverity getSeverity(@NotNull RefElement element) {
    final PsiElement psiElement = element.getPointer().getContainingFile();
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

  protected String getSeverityDelegateName() {
    return getToolWrapper().getShortName();
  }

  protected static String getTextAttributeKey(@NotNull Project project,
                                              @NotNull HighlightSeverity severity,
                                              @NotNull ProblemHighlightType highlightType) {
    if (highlightType == ProblemHighlightType.LIKE_DEPRECATED) {
      return HighlightInfoType.DEPRECATED.getAttributesKey().getExternalName();
    }
    if (highlightType == ProblemHighlightType.LIKE_UNKNOWN_SYMBOL && severity == HighlightSeverity.ERROR) {
      return HighlightInfoType.WRONG_REF.getAttributesKey().getExternalName();
    }
    if (highlightType == ProblemHighlightType.LIKE_UNUSED_SYMBOL) {
      return HighlightInfoType.UNUSED_SYMBOL.getAttributesKey().getExternalName();
    }
    SeverityRegistrar registrar = ProjectInspectionProfileManager.getInstance(project).getSeverityRegistrar();
    return registrar.getHighlightInfoTypeBySeverity(severity).getAttributesKey().getExternalName();
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
  public void exportResults(@NotNull final Element parentNode,
                            @NotNull final Predicate<RefEntity> excludedEntities,
                            @NotNull final Predicate<CommonProblemDescriptor> excludedDescriptors) {
    getRefManager().iterate(new RefVisitor(){
      @Override
      public void visitElement(@NotNull RefEntity elem) {
        if (!excludedEntities.test(elem)) {
          exportResults(parentNode, elem, excludedDescriptors);
        }
      }
    });
  }

  @Override
  public void addProblemElement(RefEntity refElement, @NotNull CommonProblemDescriptor... descriptions){
    addProblemElement(refElement, true, descriptions);
  }

  @Override
  public void addProblemElement(final RefEntity refElement, boolean filterSuppressed, @NotNull final CommonProblemDescriptor... descriptors) {
    if (refElement == null) return;
    if (descriptors.length == 0) return;
    if (filterSuppressed) {
      if (myContext.getOutputPath() == null || !(myToolWrapper instanceof LocalInspectionToolWrapper)) {
        synchronized (lock) {
          Map<RefEntity, CommonProblemDescriptor[]> problemElements = getProblemElements();
          CommonProblemDescriptor[] problems = problemElements.get(refElement);
          problems = problems == null ? descriptors : mergeDescriptors(problems, descriptors);
          problemElements.put(refElement, problems);
        }
        for (CommonProblemDescriptor description : descriptors) {
          getProblemToElements().put(description, refElement);
          collectQuickFixes(description.getFixes(), refElement);
        }
      }
      else {
        writeOutput(descriptors, refElement);
      }
    }
    else { //just need to collect problems
      for (CommonProblemDescriptor descriptor : descriptors) {
        getProblemToElements().put(descriptor, refElement);
      }
    }

    final GlobalInspectionContextImpl context = getContext();
    if (context.isViewClosed() || !(refElement instanceof RefElement)) {
      return;
    }
    if (myToolWrapper instanceof LocalInspectionToolWrapper && !ApplicationManager.getApplication().isUnitTestMode()) {
      context.initializeViewIfNeed().doWhenDone(() -> context.getView().addProblemDescriptors(myToolWrapper, refElement, descriptors));
    }
  }

  @NotNull
  public static CommonProblemDescriptor[] mergeDescriptors(@NotNull CommonProblemDescriptor[] problems1,
                                                            @NotNull CommonProblemDescriptor[] problems2) {
    CommonProblemDescriptor[] out = new CommonProblemDescriptor[problems1.length + problems2.length];
    int o = problems1.length;
    Equality<CommonProblemDescriptor> equality = (o1, o2) -> {
      if (o1 instanceof ProblemDescriptor) {
        ProblemDescriptorBase p1 = (ProblemDescriptorBase)o1;
        ProblemDescriptorBase p2 = (ProblemDescriptorBase)o2;
        if (!Comparing.equal(p1.getDescriptionTemplate(), p2.getDescriptionTemplate())) return false;
        if (!Comparing.equal(p1.getTextRange(), p2.getTextRange())) return false;
        if (!Comparing.equal(p1.getHighlightType(), p2.getHighlightType())) return false;
        if (!Comparing.equal(p1.getProblemGroup(), p2.getProblemGroup())) return false;
        if (!Comparing.equal(p1.getStartElement(), p2.getStartElement())) return false;
        if (!Comparing.equal(p1.getEndElement(), p2.getEndElement())) return false;
      }
      else {
        if (!o1.toString().equals(o2.toString())) return false;
      }
      return true;
    };
    for (CommonProblemDescriptor descriptor : problems2) {
      if (ArrayUtil.indexOf(problems1, descriptor, equality) == -1) {
        out[o++] = descriptor;
      }
    }
    System.arraycopy(problems1, 0, out, 0, problems1.length);
    return Arrays.copyOfRange(out, 0, o);
  }

  @Override
  public InspectionNode getToolNode() {
    return myToolNode;
  }

  protected boolean isDisposed() {
    return isDisposed;
  }

  private synchronized void writeOutput(@NotNull final CommonProblemDescriptor[] descriptions, @NotNull RefEntity refElement) {
    final Element parentNode = new Element(InspectionsBundle.message("inspection.problems"));
    exportResults(descriptions, refElement, parentNode, d -> false);
    final List<Element> list = parentNode.getChildren();

    @NonNls final String ext = ".xml";
    final String fileName = myContext.getOutputPath() + File.separator + myToolWrapper.getShortName() + ext;
    final PathMacroManager pathMacroManager = PathMacroManager.getInstance(getContext().getProject());
    PrintWriter printWriter = null;
    try {
      FileUtil.createDirectory(new File(myContext.getOutputPath()));
      final File file = new File(fileName);
      final StringWriter writer = new StringWriter();
      if (!file.exists()) {
        writer.append("<").append(InspectionsBundle.message("inspection.problems")).append(" " + GlobalInspectionContextBase.LOCAL_TOOL_ATTRIBUTE + "=\"")
          .append(Boolean.toString(myToolWrapper instanceof LocalInspectionToolWrapper)).append("\">\n");
      }
      for (Element element : list) {
        pathMacroManager.collapsePaths(element);
        JDOMUtil.writeElement(element, writer, "\n");
      }
      printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName, true), CharsetToolkit.UTF8_CHARSET)));
      printWriter.append("\n");
      printWriter.append(writer.toString());
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      if (printWriter != null) {
        printWriter.close();
      }
    }
  }

  @Override
  @NotNull
  public Collection<CommonProblemDescriptor> getProblemDescriptors() {
    return getProblemToElements().keySet();
  }

  private void collectQuickFixes(final QuickFix[] fixes, @NotNull RefEntity refEntity) {
    if (fixes != null && fixes.length != 0) {
      Set<QuickFix> localQuickFixes = getQuickFixActions().computeIfAbsent(refEntity, k -> new HashSet<>());
      ContainerUtil.addAll(localQuickFixes, fixes);
    }
  }

  @Override
  public void ignoreElement(@NotNull final RefEntity refEntity) {
    final CommonProblemDescriptor[] removedDescriptors = getProblemElements().remove(refEntity);
    if (removedDescriptors != null) {
      for (CommonProblemDescriptor descriptor : removedDescriptors) {
        getProblemToElements().remove(descriptor);
      }
    }

    getQuickFixActions().remove(refEntity);
  }

  @Override
  public void ignoreCurrentElement(RefEntity refEntity) {
    if (refEntity == null) return;
    getIgnoredElements().put(refEntity, mergeDescriptors(getIgnoredElements().getOrDefault(refEntity, CommonProblemDescriptor.EMPTY_ARRAY),
                                                         getProblemElements().getOrDefault(refEntity, CommonProblemDescriptor.EMPTY_ARRAY)));
  }

  @Override
  public void amnesty(RefEntity refEntity, CommonProblemDescriptor descriptor) {
    final CommonProblemDescriptor[] ignoredDescriptors = getIgnoredElements().get(refEntity);
    if (ignoredDescriptors != null) {
      final CommonProblemDescriptor[] remainElements = ArrayUtil.remove(ignoredDescriptors, descriptor);
      if (remainElements.length != 0) {
        getIgnoredElements().put(refEntity, remainElements);
      } else {
        getIgnoredElements().remove(refEntity);
      }
    }
  }

  @Override
  public void ignoreProblem(RefEntity refEntity, CommonProblemDescriptor problem, int idx) {
    if (refEntity == null) return;
    final Set<QuickFix> localQuickFixes = getQuickFixActions().get(refEntity);
    final QuickFix[] fixes = problem.getFixes();
    if (isIgnoreProblem(fixes, localQuickFixes, idx)){
      getProblemToElements().remove(problem);
      Map<RefEntity, CommonProblemDescriptor[]> problemElements = getProblemElements();
      synchronized (lock) {
        CommonProblemDescriptor[] descriptors = problemElements.get(refEntity);
        if (descriptors != null) {
          ArrayList<CommonProblemDescriptor> newDescriptors = new ArrayList<>(Arrays.asList(descriptors));
          newDescriptors.remove(problem);
          CommonProblemDescriptor[] newDescriptorsAsArray = newDescriptors.toArray(new CommonProblemDescriptor[newDescriptors.size()]);
          getQuickFixActions().put(refEntity, null);
          if (!newDescriptors.isEmpty()) {
            problemElements.put(refEntity, newDescriptorsAsArray);
            for (CommonProblemDescriptor descriptor : newDescriptors) {
              collectQuickFixes(descriptor.getFixes(), refEntity);
            }
          }
          ignoreProblemElement(refEntity, newDescriptorsAsArray, problem);
        }
      }
    }
  }

  private void ignoreProblemElement(RefEntity refEntity, CommonProblemDescriptor[] newDescriptors, CommonProblemDescriptor toIgnore){
    if (newDescriptors != null && newDescriptors.length == 0) {
      newDescriptors = null;
    }
    if (newDescriptors == null) {
      getProblemElements().remove(refEntity);
    } else {
      getProblemElements().put(refEntity, newDescriptors);
    }
    CommonProblemDescriptor[] oldIgnored = getIgnoredElements().getOrDefault(refEntity, CommonProblemDescriptor.EMPTY_ARRAY);
    CommonProblemDescriptor[] update = new CommonProblemDescriptor[oldIgnored.length + 1];
    System.arraycopy(oldIgnored, 0, update, 0, oldIgnored.length);
    update[update.length - 1] = toIgnore;
    getIgnoredElements().put(refEntity, update);
  }

  @Override
  public void ignoreCurrentElementProblem(RefEntity refEntity, CommonProblemDescriptor descriptor) {
    CommonProblemDescriptor[] descriptors = getIgnoredElements().get(refEntity);
    if (descriptors == null) {
      descriptors = CommonProblemDescriptor.EMPTY_ARRAY;
    }
    getIgnoredElements().put(refEntity, ArrayUtil.append(descriptors, descriptor));
  }

  private static boolean isIgnoreProblem(QuickFix[] problemFixes, Set<QuickFix> fixes, int idx){
    if (problemFixes == null || fixes == null) {
      return true;
    }
    if (problemFixes.length <= idx){
      return true;
    }
    for (QuickFix fix : problemFixes) {
      if (fix != problemFixes[idx] && !fixes.contains(fix)){
        return false;
      }
    }
    return true;
  }

  @Override
  public void cleanup() {
    synchronized (lock) {
      myProblemElements.clear();
      myProblemToElements.clear();
      myQuickFixActions.clear();
      myIgnoredElements.clear();
      myContents.clear();
      myModulesProblems.clear();
    }

    isDisposed = true;
  }

  @Override
  public void finalCleanup() {
    cleanup();
  }

  @Override
  @Nullable
  public CommonProblemDescriptor[] getDescriptions(@NotNull RefEntity refEntity) {
    final CommonProblemDescriptor[] problems = getProblemElements().get(refEntity);
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
  public void exportResults(@NotNull final Element parentNode,
                            @NotNull RefEntity refEntity,
                            @NotNull Predicate<CommonProblemDescriptor> isDescriptorExcluded) {
    synchronized (lock) {
      if (getProblemElements().containsKey(refEntity)) {
        CommonProblemDescriptor[] descriptions = getDescriptions(refEntity);
        if (descriptions != null) {
          exportResults(descriptions, refEntity, parentNode, isDescriptorExcluded);
        }
      }
    }
  }

  private void exportResults(@NotNull final CommonProblemDescriptor[] descriptors,
                             @NotNull RefEntity refEntity,
                             @NotNull Element parentNode,
                             @NotNull Predicate<CommonProblemDescriptor> isDescriptorExcluded) {
    for (CommonProblemDescriptor descriptor : descriptors) {
      if (isDescriptorExcluded.test(descriptor)) continue;
      @NonNls final String template = descriptor.getDescriptionTemplate();
      int line = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getLineNumber() : -1;
      final PsiElement psiElement = descriptor instanceof ProblemDescriptor ? ((ProblemDescriptor)descriptor).getPsiElement() : null;
      @NonNls String problemText = StringUtil.replace(StringUtil.replace(template, "#ref", psiElement != null ? ProblemDescriptorUtil
        .extractHighlightedText(descriptor, psiElement) : ""), " #loc ", " ");

      Element element = refEntity.getRefManager().export(refEntity, parentNode, line);
      if (element == null) return;
      @NonNls Element problemClassElement = new Element(InspectionsBundle.message("inspection.export.results.problem.element.tag"));
      problemClassElement.addContent(myToolWrapper.getDisplayName());

      final HighlightSeverity severity;
      if (refEntity instanceof RefElement){
        final RefElement refElement = (RefElement)refEntity;
        severity = getSeverity(refElement);
      }
      else {
        final InspectionProfile profile = InspectionProjectProfileManager.getInstance(getContext().getProject()).getCurrentProfile();
        final HighlightDisplayLevel level = profile.getErrorLevel(HighlightDisplayKey.find(myToolWrapper.getShortName()), psiElement);
        severity = level.getSeverity();
      }

      if (severity != null) {
        ProblemHighlightType problemHighlightType = descriptor instanceof ProblemDescriptor
                                                    ? ((ProblemDescriptor)descriptor).getHighlightType()
                                                    : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
        final String attributeKey = getTextAttributeKey(getRefManager().getProject(), severity, problemHighlightType);
        problemClassElement.setAttribute("severity", severity.myName);
        problemClassElement.setAttribute("attribute_key", attributeKey);
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
      try {
        Element descriptionElement = new Element(InspectionsBundle.message("inspection.export.results.description.tag"));
        descriptionElement.addContent(problemText);
        element.addContent(descriptionElement);
      }
      catch (IllegalDataException e) {
        //noinspection HardCodedStringLiteral,UseOfSystemOutOrSystemErr
        System.out.println("Cannot save results for " + refEntity.getName() + ", inspection which caused problem: " + myToolWrapper.getShortName());
      }
    }
  }

  @Override
  public boolean hasReportedProblems() {
    return !myProblemToElements.isEmpty();
  }

  @Override
  public void updateContent() {
    myContents.clear();
    myModulesProblems.clear();
    updateProblemElements();
  }

  protected void updateProblemElements() {
    final Set<RefEntity> elements = getProblemElements().keySet();
    for (RefEntity element : elements) {
      if (getContext().getUIOptions().FILTER_RESOLVED_ITEMS && getIgnoredElements().containsKey(element)) continue;
      if (element instanceof RefModule) {
        myModulesProblems.add((RefModule)element);
      }
      else {
        String groupName = element instanceof RefElement ? element.getRefManager().getGroupName((RefElement)element) : element.getQualifiedName() ;
        registerContentEntry(element, groupName);
      }
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

  @NotNull
  @Override
  public Set<RefModule> getModuleProblems() {
    return myModulesProblems;
  }

  @Override
  @NotNull
  public QuickFixAction[] getQuickFixes(@NotNull final RefEntity[] refElements, InspectionTree tree) {
    return extractActiveFixes(refElements, getProblemElements(), tree != null ? tree.getSelectedDescriptors() : null);
  }

  @Override
  @NotNull
  public QuickFixAction[] extractActiveFixes(@NotNull RefEntity[] refElements,
                                             @NotNull Map<RefEntity, CommonProblemDescriptor[]> descriptorMap,
                                             @Nullable CommonProblemDescriptor[] allowedDescriptors) {
    final Set<CommonProblemDescriptor> allowedDescriptorSet = allowedDescriptors == null ? null : ContainerUtil.newHashSet(allowedDescriptors);
    Map<String, LocalQuickFixWrapper> result = null;
    for (RefEntity refElement : refElements) {
      final CommonProblemDescriptor[] descriptors = descriptorMap.get(refElement);
      if (descriptors == null) continue;
      for (CommonProblemDescriptor d : descriptors) {
        if (allowedDescriptorSet != null && !allowedDescriptorSet.contains(d)) {
          continue;
        }
        QuickFix[] fixes = d.getFixes();
        if (fixes == null || fixes.length == 0) continue;
        if (result == null) {
          result = new HashMap<>();
          for (QuickFix fix : fixes) {
            if (fix == null) continue;
            result.put(fix.getFamilyName(), new LocalQuickFixWrapper(fix, myToolWrapper));
          }
        }
        else {
          for (String familyName : new ArrayList<>(result.keySet())) {
            boolean isFound = false;
            for (QuickFix fix : fixes) {
              if (fix == null) continue;
              if (familyName.equals(fix.getFamilyName())) {
                isFound = true;
                final LocalQuickFixWrapper quickFixAction = result.get(fix.getFamilyName());
                LOG.assertTrue(getFixClass(fix).equals(getFixClass(quickFixAction.getFix())),
                               "QuickFix-es with the same getFamilyName() should be the same class instances. " +
                               "Please assign reported exception for the fix \"" + fix.getClass().getName() + "\" developer");
                try {
                  quickFixAction.setText(StringUtil.escapeMnemonics(fix.getFamilyName()));
                }
                catch (AbstractMethodError e) {
                  //for plugin compatibility
                  quickFixAction.setText("Name is not available");
                }
                break;
              }
            }
            if (!isFound) {
              result.remove(familyName);
              if (result.isEmpty()) {
                return QuickFixAction.EMPTY;
              }
            }
          }
        }
      }
    }
    return result == null || result.isEmpty() ? QuickFixAction.EMPTY : result.values().toArray(new QuickFixAction[result.size()]);
  }

  private static Class getFixClass(QuickFix fix) {
    return fix instanceof ActionClassHolder ? ((ActionClassHolder)fix).getActionClass() : fix.getClass();
  }

  @Override
  public RefEntity getElement(@NotNull CommonProblemDescriptor descriptor) {
    return getProblemToElements().get(descriptor);
  }

  @Override
  public void ignoreProblem(@NotNull CommonProblemDescriptor descriptor, @NotNull QuickFix fix) {
    RefEntity refElement = getProblemToElements().get(descriptor);
    if (refElement != null) {
      final QuickFix[] fixes = descriptor.getFixes();
      for (int i = 0; i < fixes.length; i++) {
        if (fixes[i] == fix){
          ignoreProblem(refElement, descriptor, i);
          return;
        }
      }
    }
  }


  @Override
  public boolean isElementIgnored(final RefEntity element) {
    return getIgnoredElements().containsKey(element);
  }

  @Override
  public boolean isProblemResolved(RefEntity refEntity, CommonProblemDescriptor descriptor) {
    if (descriptor == null) return true;
    CommonProblemDescriptor[] descriptors = getIgnoredElements().get(refEntity);
    return descriptors != null && ArrayUtil.contains(descriptor, descriptors);
  }

  @NotNull
  @Override
  public Set<RefEntity> getIgnoredRefElements() {
    return getIgnoredElements().keySet();
  }

  @Override
  @NotNull
  public Map<RefEntity, CommonProblemDescriptor[]> getProblemElements() {
    return myProblemElements;
  }

  @NotNull
  private Map<CommonProblemDescriptor, RefEntity> getProblemToElements() {
    return myProblemToElements;
  }

  @NotNull
  private Map<RefEntity, Set<QuickFix>> getQuickFixActions() {
    return myQuickFixActions;
  }

  @NotNull
  @Override
  public Map<RefEntity, CommonProblemDescriptor[]> getIgnoredElements() {
    return myIgnoredElements;
  }

  @Override
  public void createToolNode(@NotNull GlobalInspectionContextImpl globalInspectionContext, @NotNull InspectionNode node,
                             @NotNull InspectionRVContentProvider provider,
                             @NotNull InspectionTreeNode parentNode,
                             boolean showStructure,
                             boolean groupBySeverity) {
    myToolNode = node;
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
        fix.applyFix(project, problemDescriptor); //todo check type consistency
      }

      @Override
      public boolean startInWriteAction() {
        return true;
      }
    };
  }
}
