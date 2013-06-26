package com.intellij.navigation
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.GotoClassModel2
import com.intellij.openapi.application.ModalityState
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.Consumer
import com.intellij.util.concurrency.Semaphore
/**
 * @author peter
 */
class ChooseByNameTest extends LightCodeInsightFixtureTestCase {

  public void "test trivial goto class"() {
    def xxClass = myFixture.addClass("class Xxxxx {}")
    def fooXxClass = myFixture.addClass("class FooXxxxx {}")
    List<Object> elements = createPopup(new GotoClassModel2(project), "Xxx")
    assert elements[0] == xxClass
    assert elements[2] == fooXxClass
  }

  private List<Object> createPopup(ChooseByNameModel model, String text) {
    def popup = ChooseByNamePopup.createPopup(project, model, (PsiElement)null, "")
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

  @Override
  protected boolean runInDispatchThread() {
    return false
  }

  @Override
  protected void invokeTestRunnable(Runnable runnable) throws Exception {
    runnable.run()
  }
}
