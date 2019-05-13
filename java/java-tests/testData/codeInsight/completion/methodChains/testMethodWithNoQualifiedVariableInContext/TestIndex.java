/**
 * @author Dmitry Batkovich <dmitry.batkovich@jetbrains.com>
 */
public class TestIndex {
}

class CP1 implements CompletionProvider {
  public void addCompletions(CompletionParameters p) {
    p.getPosition();
  }
}

class CP2 implements CompletionProvider {
  public void addCompletions(CompletionParameters p) {
    p.getPosition();
  }
}

class CP3 implements CompletionProvider {
  public void addCompletions(CompletionParameters p) {
    p.getPosition();
  }
}

class CP4 implements CompletionProvider {
  public void addCompletions(CompletionParameters p) {
    p.getPosition();
  }
}

interface CompletionProvider {
  void addCompletions(CompletionParameters p);
}

interface CompletionParameters {
  PsiElement getPosition();
}

interface PsiElement {

}
