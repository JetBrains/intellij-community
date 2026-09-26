import org.slf4j.*;

class CharLiteral {

  Logger LOG = LoggerFactory.getLogger(CharLiteral.class);

  void f(String docId, String fieldName) {
    LOG.info<caret>("Segment ordinal not found for docID" + docId + "; field: \"" + fieldName + '"');
  }

}