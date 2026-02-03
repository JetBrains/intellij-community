
import com.mycom.FirstPreviewFeature;

class Main {
  public Main(String s) { }
  {
    var a = new <warning descr="com.mycom.FirstPreviewFeature.Outer.Inner is a preview API and may be removed in a future release"><warning descr="com.mycom.FirstPreviewFeature.Outer is a preview API and may be removed in a future release"><warning descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">FirstPreviewFeature</warning>.Outer</warning>.Inner</warning>();
    a.<warning descr="com.mycom.FirstPreviewFeature.Outer.Inner#z is a preview API and may be removed in a future release">z</warning>();
    Runnable r = a::<warning descr="com.mycom.FirstPreviewFeature.Outer.Inner#z is a preview API and may be removed in a future release">z</warning>;
    new Main(<warning descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">FirstPreviewFeature</warning>.<warning descr="com.mycom.FirstPreviewFeature#KEY is a preview API and may be removed in a future release">KEY</warning>);
    new Main(<warning descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">FirstPreviewFeature</warning>.<warning descr="com.mycom.FirstPreviewFeature#KEY is a preview API and may be removed in a future release">KEY</warning> + "");
    new Main("" + <warning descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">FirstPreviewFeature</warning>.<warning descr="com.mycom.FirstPreviewFeature#KEY is a preview API and may be removed in a future release">KEY</warning>);
  }
}