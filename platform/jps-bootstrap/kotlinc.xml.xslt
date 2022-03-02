<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="text" version="1.0" encoding="UTF-8" />
  <xsl:template match="/project/component[@name='KotlinJpsPluginSettings']">
    <xsl:for-each select="option[@name='version']">
      <xsl:value-of select="@value"/>
    </xsl:for-each>
  </xsl:template>
</xsl:stylesheet>