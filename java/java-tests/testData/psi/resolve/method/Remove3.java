package psi.resolve.method;

import com.intellij.util.containers.trove.THashMap;

public class Remove3{
    THashMap map = new THashMap();
    
    void foo(){
        map.<caret>size();
    }
}
