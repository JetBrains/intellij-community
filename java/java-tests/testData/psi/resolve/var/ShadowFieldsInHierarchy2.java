
interface Base {
    public static final int EXIT_ON_CLOSE = 3;

}
class E implements Base {
   public static final int EXIT_ON_CLOSE = 3;
   void show(){}
}
class S {
    private void h() {
        new E() {
            {
                int o = <ref>EXIT_ON_CLOSE;
            }
        }.show();
    }
}
