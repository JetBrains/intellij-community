public class Main {

  public void main(String[] args) {
    Aaaa a = new Bbbb(<caret>2);
  }

  class Aaaa {
    int aaa;

    Aaaa(int aaa) {
      this.aaa = aaa;
    }
  }

  class Bbbb extends Aaaa{
    Bbbb(int aaa) {
      super(aaa);
    }
  }

}
