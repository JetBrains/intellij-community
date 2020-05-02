package extractMethod;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Iterator;

public class SCR27887 {
    public int publishx(OutputStream out, boolean includeCode) throws IOException {
        if (VERBOSE) System.err.println("PUBLISH: publishing subsystem '" + subsystem.refQualifiedIdentifyingName() + "' with" + (includeCode ? "" : "out") + " code");
        ZippingXMLGeneratorFactory genFac = new ZippingXMLGeneratorFactory(out);
//========
        RefObjectUList included = newMethod(genFac);
//========
        if (includeCode) {
            for (Iterator i = subsystem.getModule().iterator(); i.hasNext();) {
                OptimalModule module = (OptimalModule) i.next();
                if (module.getPublished()) {
                    FileObject[] files = getModuleProducts(module);
                    if (files != null && files.length > 0) {
                        for (int j = 0; j < files.length; j++) {
                            FileObject file = files[j];
                            OutputStream os = genFac.getOutputStream(file.getFileName());
                            InputStream is = file.getInputStream();
                            try {
                                copyStream(is, os);
                            } finally {
                                os.close();
                                is.close();
                            }
                        }
                    }
                }
            }
        }
        genFac.close();
        return included.size();
    }

    @NotNull
    private RefObjectUList newMethod(ZippingXMLGeneratorFactory genFac) {
        RefObjectUList included = makeIncludedSet();
        if (!included.isEmpty()) {
            ScatteringDocBuilder docBuilder = new MyDocBuilder(repository, included);
            new RepositorySaver(repository).saveTo(genFac, docBuilder);
        }
        return included;
    }
}
