
import <error descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">com.mycom.FirstPreviewFeature</error>;
import <warning descr="com.mycom.FirstPreviewFeatureReflective is a preview API and may be removed in a future release">com.mycom.FirstPreviewFeatureReflective</warning>;

class Main {
  {
    <error descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">FirstPreviewFeature</error>.g();
    Runnable r1 = <error descr="com.mycom.FirstPreviewFeature is a preview API and may be removed in a future release">FirstPreviewFeature</error>::g;
    <warning descr="com.mycom.FirstPreviewFeatureReflective#g is a preview API and may be removed in a future release"><warning descr="com.mycom.FirstPreviewFeatureReflective is a preview API and may be removed in a future release">FirstPreviewFeatureReflective</warning>.g</warning>();
    Runnable r2 = <warning descr="com.mycom.FirstPreviewFeatureReflective#g is a preview API and may be removed in a future release"><warning descr="com.mycom.FirstPreviewFeatureReflective is a preview API and may be removed in a future release">FirstPreviewFeatureReflective</warning>::g</warning>;
  }
}