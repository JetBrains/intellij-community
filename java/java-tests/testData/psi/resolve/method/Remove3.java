package psi.resolve.method;

import com.intellij.util.containers.trove.THashMap;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 17.01.2003
 * Time: 15:08:26
 * To change this template use Options | File Templates.
 */
public class Remove3{
    THashMap map = new THashMap();
    
    void foo(){
        map.<ref>size();
    }
}
