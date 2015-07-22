package org.jetbrains.testme.instrumentation;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    private static final int STRING_LENGTH_THRESHOLD = 255;
    private static final int STRING_HEADER_SIZE = 1;

    private static void writeUTF(DataOutput storage, byte[] buffer, String value) throws IOException {
        int len = value.length();

        if (len < STRING_LENGTH_THRESHOLD) {
            buffer[0] = (byte)len;
            boolean isAscii = true;
            for (int i = 0; i < len; i++) {
                char c = value.charAt(i);
                if (c >= 128) {
                    isAscii = false;
                    break;
                }
                buffer[i + STRING_HEADER_SIZE] = (byte)c;
            }
            if (isAscii) {
                storage.write(buffer, 0, len + STRING_HEADER_SIZE);
                return;
            }
        }

        storage.writeByte((byte)0xFF);
        storage.writeUTF(value);
    }

    private static void writeINT(DataOutput record, int val) throws IOException {
        if (0 <= val && val < 192) {
            record.writeByte(val);
        }
        else {
            record.writeByte(192 + (val & 0x3F));
            val >>>= 6;
            while (val >= 128) {
                record.writeByte((val & 0x7F) | 0x80);
                val >>>= 7;
            }
            record.writeByte(val);
        }
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
            try {
                os = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(traceFile), 64 * 1024));
                final byte[] buffer = new byte[STRING_LENGTH_THRESHOLD + STRING_HEADER_SIZE];

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

                writeINT(os, classToUsedMethods.size());
                for(Map.Entry<String, boolean[]> e: myTrace2.entrySet()) {
                    final boolean[] used = e.getValue();
                    final String className = e.getKey();

                    Integer integer = classToUsedMethods.get(className);
                    if (integer == null) continue;;

                    int usedMethodsCount = integer;

                    writeUTF(os, buffer, className);
                    writeINT(os, usedMethodsCount);

                    String[] methodNames = myTrace3.get(className);
                    for (int i = 0, len = used.length; i < len; ++i) {
                        // we check usedMethodCount here since used can still be updated by other threads
                        if (used[i] && usedMethodsCount-- > 0) writeUTF(os, buffer, methodNames[i]);
                    }
                }
            }
            finally {
                if (os != null) {
                    os.close();
                }
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
