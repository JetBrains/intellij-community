// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.net.ssl;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.io.DigestUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.security.auth.x500.X500Principal;
import java.security.Principal;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Mikhail Golubev
 */
@SuppressWarnings("UnusedDeclaration")
public final class CertificateWrapper {
  @NonNls public static final String NOT_AVAILABLE = "N/A";

  private final X509Certificate myCertificate;
  private final Map<String, String> myIssuerFields;
  private final Map<String, String> mySubjectFields;

  public CertificateWrapper(@NotNull X509Certificate certificate) {
    myCertificate = certificate;
    myIssuerFields = extractFields(certificate.getIssuerX500Principal());
    mySubjectFields = extractFields(certificate.getSubjectX500Principal());
  }

  /**
   * @param name - Common name of desired issuer field
   * @return field value of {@link #NOT_AVAILABLE}. if it doesn't exist
   */
  public @NotNull String getIssuerField(@NotNull CommonField name) {
    String field = myIssuerFields.get(name.getShortName());
    return field == null ? NOT_AVAILABLE : field;
  }

  /**
   * @param name - Common name of desired subject field
   * @return field value of {@link #NOT_AVAILABLE}, if it doesn't exist
   */
  public @NotNull @NlsSafe String getSubjectField(@NotNull CommonField name) {
    String field = mySubjectFields.get(name.getShortName());
    return field == null ? NOT_AVAILABLE : field;
  }

  /**
   * Returns SHA-256 fingerprint of the certificate.
   *
   * @return SHA-256 fingerprint or {@link #NOT_AVAILABLE} in case of any error
   */
  public @NotNull String getSha256Fingerprint() {
    try {
      return DigestUtil.sha256Hex(myCertificate.getEncoded());
    }
    catch (CertificateEncodingException e) {
      return NOT_AVAILABLE;
    }
  }

  /**
   * Returns SHA-1 fingerprint of the certificate.
   *
   * @return SHA-1 fingerprint or {@link #NOT_AVAILABLE} in case of any error
   */
  public @NotNull String getSha1Fingerprint() {
    try {
      return DigestUtil.sha1Hex(myCertificate.getEncoded());
    }
    catch (Exception e) {
      return NOT_AVAILABLE;
    }
  }

  /**
   * Check whether certificate is valid. It's considered invalid it it either expired or
   * not yet valid.
   *
   * @see #isExpired()
   * @see #isNotYetValid()
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

  public boolean isExpired() {
    return new Date().getTime() > myCertificate.getNotAfter().getTime();
  }

  public boolean isNotYetValid() {
    return new Date().getTime() < myCertificate.getNotBefore().getTime();
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

  public @NotNull String getSerialNumber() {
    return myCertificate.getSerialNumber().toString();
  }

  public X509Certificate getCertificate() {
    return myCertificate;
  }

  public X500Principal getIssuerX500Principal() {
    return myCertificate.getIssuerX500Principal();
  }

  public X500Principal getSubjectX500Principal() {
    return myCertificate.getSubjectX500Principal();
  }

  public Date getNotBefore() {
    return myCertificate.getNotBefore();
  }

  public Date getNotAfter() {
    return myCertificate.getNotAfter();
  }

  public Map<String, String> getIssuerFields() {
    return myIssuerFields;
  }

  public Map<String, @Nls String> getSubjectFields() {
    return mySubjectFields;
  }

  // E.g. CN=*.github.com,O=GitHub\, Inc.,L=San Francisco,ST=California,C=US
  private static Map<String, String> extractFields(@NotNull Principal principal) {
    Map<String, String> fields = new HashMap<>();
    for (String field : principal.getName().split("(?<!\\\\),")) {
      String[] parts = field.trim().split("=", 2);
      if (parts.length != 2) {
        continue;
      }
      fields.put(parts[0], parts[1].replaceAll("\\\\,", ","));
    }
    return Collections.unmodifiableMap(fields);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CertificateWrapper && myCertificate.equals(((CertificateWrapper)other).getCertificate());
  }

  @Override
  public int hashCode() {
    return myCertificate.hashCode();
  }

  /**
   * Find out full list of names from specification.
   * See http://tools.ietf.org/html/rfc5280#section-4.1.2.2 for details.
   */
  public enum CommonField {
    COMMON_NAME("CN", "Common Name"),
    ORGANIZATION("O", "Organization"),
    ORGANIZATION_UNIT("OU", "Organizational Unit"),
    LOCATION("L", "Locality"),
    COUNTRY("C", "Country"),
    STATE("ST", "State or Province");

    private final String myShortName;
    private final String myLongName;

    CommonField(@NotNull String shortName, @NotNull String longName) {
      myShortName = shortName;
      myLongName = longName;
    }

    public @NlsSafe String getShortName() {
      return myShortName;
    }

    public @NlsSafe String getLongName() {
      return myLongName;
    }
  }
}
