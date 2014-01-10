package com.intellij.util.net;

import com.intellij.util.containers.HashMap;
import org.apache.commons.codec.digest.DigestUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.x500.X500Principal;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Map;

/**
 * @author Mikhail Golubev
 */
@SuppressWarnings("UnusedDeclaration")
public class CertificateWrapper {
  @NonNls public static final String NOT_AVAILABLE = "N/A";

  private final X509Certificate myCertificate;
  private final Map<String, String> myIssuerFields;
  private final Map<String, String> mySubjectFields;

  public CertificateWrapper(@NotNull X509Certificate certificate) {
    myCertificate = certificate;
    myIssuerFields = extractFields(certificate.getIssuerX500Principal());
    mySubjectFields = extractFields(certificate.getSubjectX500Principal());
  }

  @NotNull
  public String getIssuerField(@NotNull CommonField name) {
    String field = myIssuerFields.get(name.getShortName());
    return field == null ? NOT_AVAILABLE : field;
  }

  @NotNull
  public String getSubjectField(@NotNull CommonField name) {
    String field = mySubjectFields.get(name.getShortName());
    return field == null ? NOT_AVAILABLE : field;
  }

  /**
   * Returns SHA-256 fingerprint of the certificate.
   * Returned string is in upper case and octets are delimited by spaces.
   *
   * @return SHA-256 fingerprint or {@link #NOT_AVAILABLE} in case of any error
   */
  @NotNull
  public String getSha256Fingerprint() {
    try {
      return formatHex(DigestUtils.sha256Hex(myCertificate.getEncoded()));
    }
    catch (CertificateEncodingException e) {
      return NOT_AVAILABLE;
    }
  }

  /**
   * Returns SHA-1 fingerprint of the certificate.
   * Returned string is in upper case and octets are delimited by spaces.
   *
   * @return SHA-1 fingerprint or {@link #NOT_AVAILABLE} in case of any error
   */
  private static String getSha1Fingerprint(X509Certificate certificate) {
    try {
      return formatHex(DigestUtils.sha1Hex(certificate.getEncoded()));
    }
    catch (Exception e) {
      return NOT_AVAILABLE;
    }
  }

  /**
   * Check whether certificate is valid. It's considered invalid it it either expired or
   * not yet legal.
   */
  public boolean isValid() {
    try {
      myCertificate.checkValidity();
      return true;
    }
    catch (Exception e) {
      return false;
    }
  }

  /**
   * Check whether certificate is self-signed. It's considered self-signed if
   * its issuer and subject are the same.
   */
  public boolean isSelfSigned() {
    return myCertificate.getIssuerX500Principal().equals(myCertificate.getSubjectX500Principal());
  }

  public int getVersion() {
    return myCertificate.getVersion();
  }

  @NotNull
  public String getSerialNumber() {
    return myCertificate.getSerialNumber().toString();
  }

  private static Map<String, String> extractFields(X500Principal principal) {
    Map<String, String> fields = new HashMap<String, String>();
    for (String field : principal.getName().split(",")) {
      field = field.trim();
      String[] parts = field.split("=", 2);
      if (parts.length != 2) {
        continue;
      }
      fields.put(parts[0], parts[1]);
    }
    return fields;
  }

  @NotNull
  private static String formatHex(@NotNull String hex) {
    StringBuilder builder = new StringBuilder((int)(hex.length() * 1.5));
    for (int i = 0; i < hex.length(); i += 2) {
      builder.append(hex.substring(i, i + 2));
      builder.append(' ');
    }
    return builder.toString().toUpperCase();
  }


  /**
   * Find out full list of names from specification.
   */
  public enum CommonField {
    COMMON_NAME("CN", "Common Name"),
    ORGANIZATION("O", "Organization"),
    ORGANIZATION_UNIT("OU", "Organization Unit"),
    LOCATION("L", "Location"),
    COUNTRY("C", "Country"),
    STATE("ST", "State or Province");

    private final String myShortName;
    private final String myLongName;

    CommonField(@NotNull String shortName, @NotNull String longName) {
      myShortName = shortName;
      myLongName = longName;
    }

    public String getShortName() {
      return myShortName;
    }

    public String getLongName() {
      return myLongName;
    }
  }
}
