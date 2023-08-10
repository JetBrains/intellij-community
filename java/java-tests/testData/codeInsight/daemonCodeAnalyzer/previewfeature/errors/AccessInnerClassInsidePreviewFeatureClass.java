
import <error descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">com.mycom.FirstPreviewFeature</error>;
import <warning descr="com.mycom.FirstPreviewFeatureReflective is a preview API and may be removed in a future release">com.mycom.FirstPreviewFeatureReflective</warning>;

class Main {
  public Main(String value) { }
  {
    var a = new <error descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">FirstPreviewFeature</error>.Outer.Inner();
    var b = new <warning descr="com.mycom.FirstPreviewFeatureReflective.Outer.Inner is a preview API and may be removed in a future release"><warning descr="com.mycom.FirstPreviewFeatureReflective.Outer is a preview API and may be removed in a future release"><warning descr="com.mycom.FirstPreviewFeatureReflective is a preview API and may be removed in a future release">FirstPreviewFeatureReflective</warning>.Outer</warning>.Inner</warning>();
    <error descr="com.mycom.FirstPreviewFeature.Outer.Inner#z is a preview API and may be removed in a future release">a.z</error>();
    <warning descr="com.mycom.FirstPreviewFeatureReflective.Outer.Inner#z is a preview API and may be removed in a future release">b.z</warning>();
    Runnable r1 = <error descr="com.mycom.FirstPreviewFeature.Outer.Inner#z is a preview API and may be removed in a future release">a::z</error>;
    Runnable r2 = <warning descr="com.mycom.FirstPreviewFeatureReflective.Outer.Inner#z is a preview API and may be removed in a future release">b::z</warning>;
    new Main(<error descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">FirstPreviewFeature</error>.KEY);
    new Main(<warning descr="com.mycom.FirstPreviewFeatureReflective#KEY is a preview API and may be removed in a future release"><warning descr="com.mycom.FirstPreviewFeatureReflective is a preview API and may be removed in a future release">FirstPreviewFeatureReflective</warning>.KEY</warning>);

    new Main(<error descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">FirstPreviewFeature</error>.KEY + "");
    new Main(<warning descr="com.mycom.FirstPreviewFeatureReflective#KEY is a preview API and may be removed in a future release"><warning descr="com.mycom.FirstPreviewFeatureReflective is a preview API and may be removed in a future release">FirstPreviewFeatureReflective</warning>.KEY</warning> + "");

    new Main("" + <error descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">FirstPreviewFeature</error>.KEY);
    new Main("" + <warning descr="com.mycom.FirstPreviewFeatureReflective#KEY is a preview API and may be removed in a future release"><warning descr="com.mycom.FirstPreviewFeatureReflective is a preview API and may be removed in a future release">FirstPreviewFeatureReflective</warning>.KEY</warning>);
  }
}