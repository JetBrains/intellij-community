package org.jetbrains.testme.instrumentation;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class ProjectData {
    public static final String PROJECT_DATA_OWNER = "org/jetbrains/testme/instrumentation/ProjectData";
    public static final String TRACE_DIR = "org.jetbrains.testme.instrumentation.trace.dir";

    protected static final ProjectData ourData = new ProjectData();

    private String myTraceDir = System.getProperty(TRACE_DIR, "");

    public void setTraceDir(String traceDir) {
        myTraceDir = traceDir;
    }

    private ConcurrentMap<String, Set<String>> myTrace;
    private final ConcurrentMap<String, boolean[]> myTrace2 = new ConcurrentHashMap<String, boolean[]>();
    private final ConcurrentMap<String, String[]> myTrace3 = new ConcurrentHashMap<String, String[]>();

    public static ProjectData getProjectData() {
        return ourData;
    }
    
    public static void trace(String className, String methodSignature) {
        ourData.traceLines(className, methodSignature);
    }

    // called from instrumented code during class's static init
    public static void trace(String className, boolean[] methodFlags, String[] methodNames) {
        ourData.traceLines(className, methodFlags, methodNames);
    }
    
    public void traceLines(String className, String methodSignature) {
        if (myTrace != null) {
            Set<String> methods = myTrace.get(className);
            if (methods == null) {
                methods = new HashSet<String>();
                Set<String> previousMethods = myTrace.putIfAbsent(className, methods);
                if (previousMethods != null) methods = previousMethods;
            }
            synchronized (methods) {
                methods.add(methodSignature);
            }
        }
    }

    public synchronized void traceLines(String className, boolean[] methodFlags, String[] methodNames) {
        //System.out.println("Registering " + className);
        assert methodFlags.length == methodNames.length;
        myTrace2.put(className, methodFlags);
        myTrace3.put(className, methodNames);
    }

    private static volatile boolean traceDirDumped;

    public synchronized void testEnded(final String name) {
        //if (myTrace == null) return;
        if (!traceDirDumped) {
            ClassLoader classLoader = TestDiscoveryInstrumentator.class.getClassLoader();
            System.out.println(ourData + "; cl: " + classLoader+ "," + classLoader.getParent());
            System.out.println("Trace dir:" + myTraceDir);
            traceDirDumped = true;
        }
        new File(myTraceDir).mkdirs();
        final File traceFile = new File(myTraceDir, name + ".tr");
        try {
            if (!traceFile.exists()) {
                traceFile.createNewFile();
            }
            DataOutputStream os = null;
            Deflater def = new Deflater(1);
            try {
                os = new DataOutputStream(new DeflaterOutputStream(new BufferedOutputStream(new FileOutputStream(traceFile)), def));

                //saveOldTrace(os);

                Map<String, Integer> classToUsedMethods = new HashMap<String, Integer>();
                for(Map.Entry<String, boolean[]> e: myTrace2.entrySet()) {
                    boolean[] used = e.getValue();
                    int usedMethodsCount = 0;

                    for (boolean anUsed : used) {
                        if (anUsed) ++usedMethodsCount;
                    }

                    if (usedMethodsCount > 0) {
                        classToUsedMethods.put(e.getKey(), usedMethodsCount);
                    }
                }

                os.writeInt(classToUsedMethods.size());
                for(Map.Entry<String, boolean[]> e: myTrace2.entrySet()) {
                    final boolean[] used = e.getValue();
                    final String className = e.getKey();

                    Integer integer = classToUsedMethods.get(className);
                    if (integer == null) continue;;

                    int usedMethodsCount = integer;

                    os.writeUTF(className);
                    os.writeInt(usedMethodsCount);

                    String[] methodNames = myTrace3.get(className);
                    for (int i = 0, len = used.length; i < len; ++i) {
                        // we check usedMethodCount here since used was observed to change // ?
                        if (used[i] && usedMethodsCount-- > 0) os.writeUTF(methodNames[i]);
                    }
                }
            }
            finally {
                if (os != null) {
                    os.close();
                }
                def.end();
            }
        }
        catch (IOException e) {
           e.printStackTrace();
        }
        finally {
            myTrace = null;
        }
    }

    private void saveOldTrace(DataOutputStream os) throws IOException {
        os.writeInt(myTrace.size());
        for (Iterator<String> it = myTrace.keySet().iterator(); it.hasNext();) {
            final String classData = it.next();
            os.writeUTF(classData);
            final Set<String> methods = myTrace.get(classData);
            os.writeInt(methods.size());
            for (Iterator<String> iterator = methods.iterator(); iterator.hasNext(); ) {
                os.writeUTF(iterator.next());
            }
        }
    }

    public synchronized void testStarted(final String name) {
        //clearOldTrace();
        for(Map.Entry<String, boolean[]> e: myTrace2.entrySet()) {
            boolean[] used = e.getValue();
            for(int i = 0, len = used.length; i < len; ++i) {
                if(used[i]) used[i] = false;
            }
        }
    }

    private void clearOldTrace() {
        myTrace = new ConcurrentHashMap<String, Set<String>>();
    }
}
