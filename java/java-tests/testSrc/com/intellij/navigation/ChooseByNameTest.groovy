package com.intellij.navigation

import com.intellij.ide.util.gotoByName.ChooseByNameBase
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.GotoClassModel2
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.Consumer
import com.intellij.util.concurrency.Semaphore
/**
 * @author peter
 */
class ChooseByNameTest extends LightCodeInsightFixtureTestCase {

  public void "test goto class order by matching degree"() {
    def startMatch = myFixture.addClass("class UiUtil {}")
    def wordSkipMatch = myFixture.addClass("class UiAbstractUtil {}")
    def camelMatch = myFixture.addClass("class UberInstructionUxTopicInterface {}")
    def middleMatch = myFixture.addClass("class BaseUiUtil {}")
    def elements = getPopupElements(new GotoClassModel2(project), "uiuti")
    assert elements == [startMatch, wordSkipMatch, camelMatch, ChooseByNameBase.NON_PREFIX_SEPARATOR, middleMatch]
  }

  public void "test annotation syntax"() {
    def match = myFixture.addClass("@interface Anno1 {}")
    myFixture.addClass("class Anno2 {}")
    def elements = getPopupElements(new GotoClassModel2(project), "@Anno")
    assert elements == [match]
  }

  public void "test no result for empty patterns"() {
    myFixture.addClass("@interface Anno1 {}")
    myFixture.addClass("class Anno2 {}")

    def popup = createPopup(new GotoClassModel2(project))
    assert getPopupElements(popup, "") == []
    popup.close(false)

    assert getPopupElements(new GotoClassModel2(project), "@") == []
  }

  private List<Object> getPopupElements(ChooseByNameModel model, String text) {
    return getPopupElements(createPopup(model), text)
  }

  private static ArrayList<String> getPopupElements(ChooseByNamePopup popup, String text) {
    List<Object> elements = ['empty']
    def semaphore = new Semaphore()
    semaphore.down()
    popup.scheduleCalcElements(text, false, false, ModalityState.NON_MODAL, { set ->
      elements = set as List
      semaphore.up()
    } as Consumer<Set<?>>)
    assert semaphore.waitFor(1000)
    return elements
  }

  private ChooseByNamePopup createPopup(ChooseByNameModel model) {
    def popup = ChooseByNamePopup.createPopup(project, model, (PsiElement)null, "")
    Disposer.register(testRootDisposable, { popup.close(false) } as Disposable)
    popup
  }

  @Override
  protected boolean runInDispatchThread() {
    return false
  }

  @Override
  protected void invokeTestRunnable(Runnable runnable) throws Exception {
    runnable.run()
  }
}
