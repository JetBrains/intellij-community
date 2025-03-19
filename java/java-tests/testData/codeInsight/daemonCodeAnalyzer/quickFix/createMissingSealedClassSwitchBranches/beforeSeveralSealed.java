// "Create missing branch 'Test.Cl1'" "true-preview"
class Test {
  interface II1{}
  sealed interface II2{}
  non-sealed class Cl1 implements II2{}
  public <T extends II1 & II2> void test(T c) {
    switch (c<caret>) {
    }
  }
}