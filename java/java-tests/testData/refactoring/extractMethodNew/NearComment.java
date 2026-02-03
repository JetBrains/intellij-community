public class ExtractMethods {
  public void processLine(String line) {
    <selection>
    //final Charset charset = myProcessHandler.getCharset();
    final OutputStream outputStream = null;//myProcessHandler.getProcessInput();
    try {
      //byte[] bytes = (line + "\n").getBytes(charset.name());
      byte[] bytes = (line + "\n").getBytes();
      outputStream.write(bytes);
      outputStream.flush();
    }
    catch (IOException e) {
      // ignore
    }
    </selection>
  }

}
