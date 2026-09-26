import java.util.*;
import java.io.*;

class Test {
  public void foo(String propertiesFile) {
    Properties properties = new Properties();
    InputStream inStream = null;
    try {
      removeCustomPrefixFromProperties(propertiesFile);

      inStream = new FileInputStream(propertiesFile);
      properties.load(inStream);

      Enumeration<?> propertyNames = properties.propertyNames();
      while (propertyNames.hasMoreElements()) {
        String name = (String) propertyNames.nextElement();

        setValue(name, properties.getProperty(name));
      }

    } catch (FileNotFoundException e) {
      System.err.println(e.getMessage());
      System.exit(-1);
    }
    catch (IOException e) {
      <error descr="Cannot resolve symbol 'LOG'">LOG</error>.error(e.getMessage(), e);
    } finally {
      if (inStream != null) {
        try {
          inStream.close();
        } catch (IOException e) {
          <error descr="Cannot resolve symbol 'LOG'">LOG</error>.info(e);
        }
      }
    }
  }

  void setValue(String a, String b) {}
  void removeCustomPrefixFromProperties(String file) {}
}