package org.jetbrains.ether;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 19.11.10
 * Time: 3:05
 * To change this template use File | Settings | File Templates.
 */
public class ProjectSnapshot {
    String myProjectStructure;
    Map<String, ModuleStatus> myModuleHistories;

    public ProjectSnapshot(final String prjStruct, final Map<String, ModuleStatus> moduleHistories) {
        myProjectStructure = prjStruct;
        myModuleHistories = moduleHistories;
    }

    public String toString () {
        StringBuffer buf = new StringBuffer();

        buf.append(myModuleHistories.size() + "\n");

        for (ModuleStatus h : myModuleHistories.values()) {
            buf.append(h.toString() + "\n");
        }

        buf.append(myProjectStructure);

        return buf.toString();
    }

    public ProjectSnapshot(final String s) {
        BufferedReader rd = new BufferedReader(new StringReader(s));

        try {
            final int n = Integer.parseInt(rd.readLine());

            myModuleHistories = new HashMap<String, ModuleStatus>();

            for (int i = 0; i<n; i++) {
                ModuleStatus h = new ModuleStatus(rd.readLine());
                myModuleHistories.put(h.getName(), h);
            }

            StringBuffer buf = new StringBuffer();

            while (true) {
                final String str = rd.readLine();

                if (str == null)
                    break;

                buf.append(str);
                buf.append("\n");
            }


            myProjectStructure = buf.toString();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean structureChanged (final ProjectSnapshot p) {
        return ! p.myProjectStructure.equals(myProjectStructure);
    }
}
