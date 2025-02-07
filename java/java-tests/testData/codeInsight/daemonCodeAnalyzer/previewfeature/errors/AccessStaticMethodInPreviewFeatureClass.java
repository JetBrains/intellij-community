
import <error descr="com.mycom.FirstPreviewFeature is a preview API and is disabled by default">com.mycom.FirstPreviewFeature</error>;
import com.mycom.FirstPreviewFeatureReflective;

class Main {
  {
    <error descr="com.mycom.FirstPreviewFeature is a preview API and is disabled by default">FirstPreviewFeature</error>.g();
    Runnable r1 = <error descr="com.mycom.FirstPreviewFeature is a preview API and is disabled by default">FirstPreviewFeature</error>::g;
    <warning descr="com.mycom.FirstPreviewFeatureReflective is a reflective preview API and may be removed in a future release">FirstPreviewFeatureReflective</warning>.<warning descr="com.mycom.FirstPreviewFeatureReflective#g is a reflective preview API and may be removed in a future release">g</warning>();
    Runnable r2 = <warning descr="com.mycom.FirstPreviewFeatureReflective is a reflective preview API and may be removed in a future release">FirstPreviewFeatureReflective</warning>::<warning descr="com.mycom.FirstPreviewFeatureReflective#g is a reflective preview API and may be removed in a future release">g</warning>;
  }
}