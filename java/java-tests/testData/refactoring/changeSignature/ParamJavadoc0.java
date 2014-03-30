class X {

    /**
     * Has a method called {@link #mymethod(int, int)}.
     */
    public class TestRefactorLink {
      /**
       @return nothing
       @param y yparam
       @param z zparam
       */
        public void <caret>mymethod(int y, int z) { }
    }
}
