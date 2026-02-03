public class Main {

  public void main(String[] args) {
    Aaaa a = new B<caret>Aaaa(2);
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
