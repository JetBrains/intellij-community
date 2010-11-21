package org.jetbrains.ether;

import java.io.*;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 19.11.10
 * Time: 3:05
 * To change this template use File | Settings | File Templates.
 */
public class ProjectHistory implements Serializable {
    String myProjectStructure;
    Map<String, ModuleHistory> myModuleHistories;

    public ProjectHistory (final String prjStruct, final Map<String, ModuleHistory> moduleHistories) {
        myProjectStructure = prjStruct;
        myModuleHistories = moduleHistories;
    }

    public boolean structureChanged (final ProjectHistory p) {
        /*
        try {
            Writer fo1 = new BufferedWriter(new FileWriter("/home/db/tmp/1.history"));
            Writer fo2 = new BufferedWriter(new FileWriter("/home/db/tmp/2.history"));

            fo1.write(p.myProjectStructure);
            fo2.write(myProjectStructure);

            fo1.close();
            fo2.close();
        }
        catch (IOException e) {

        }
        */

        return ! p.myProjectStructure.equals(myProjectStructure);
    }
}
