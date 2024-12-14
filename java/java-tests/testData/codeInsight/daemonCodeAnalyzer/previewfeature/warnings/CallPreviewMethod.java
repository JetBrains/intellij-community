
import com.mycom.PreviewFeatureMethod;

class Main {
  void test(PreviewFeatureMethod m){
    m.<warning descr="com.mycom.PreviewFeatureMethod#f is a preview API and may be removed in a future release">f</warning>();
  }
}