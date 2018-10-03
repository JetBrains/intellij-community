<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0"
    xmlns:lxslt="http://xml.apache.org/xslt"
    xmlns:redirect="http://xml.apache.org/xalan/redirect"
    extension-element-prefixes="redirect">

<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

    <xsl:output method="html" indent="yes" encoding="US-ASCII"/>
    <xsl:decimal-format decimal-separator="." grouping-separator="," />

    <xsl:param name="output.dir" select="'.'"/>
    <xsl:param name="basedir" select="'.'"/>



    <!--
    Matches the root element of the data and starts the generation.
    -->
    <xsl:template match="checkstyle">
        <!-- create the sorted.html with the data -->
        <redirect:write file="{$output.dir}/sorted.html">
            <xsl:call-template name="sorted.html"/>
        </redirect:write>

        <!-- create the stylesheet.css with layout -->
        <redirect:write file="{$output.dir}/sorted.css">
            <xsl:call-template name="sorted.css"/>
        </redirect:write>

        <!-- create the switch.js for collepsing the data -->
        <redirect:write file="{$output.dir}/switch.js">
            <xsl:call-template name="switch.js"/>
        </redirect:write>
    </xsl:template>



    <!--
    Generates the HTML page with the data.
    -->
    <xsl:template name="sorted.html">
        <html>
            <head>
                <title>CheckStyle Audit</title>
                <script language="JavaScript" src="switch.js" type="text/javascript"></script>
                <link rel="stylesheet" type="text/css" href="sorted.css"/>
            </head>
            <body onload="javascript:openFirst();">
                <h1>CheckStyle Audit</h1>
                <p>Designed for use with
                  <a href='http://checkstyle.sourceforge.net/'>CheckStyle</a> and
                  <a href='http://ant.apache.org/'>Ant</a>.
                </p>
                <xsl:apply-templates select="." mode="navigation"/>
                <xsl:apply-templates select="." mode="data"/>
            </body>
        </html>
    </xsl:template>



    <!--
    Key for detecting duplicate CheckModules 
    -->
    <xsl:key name="module" match="file/error" use="@source"/>



    <!--
    Generates the navagation bar.
    --> 
    <xsl:template match="checkstyle" mode="navigation">
        <ul id="navigation">
            <xsl:for-each select="file/error[generate-id() = generate-id(key('module',@source))]">
                <xsl:sort select="@source"/>
                <xsl:variable name="last-index">
                    <xsl:call-template name="last-index-of">
                        <xsl:with-param name="txt" select="@source"/>
                        <xsl:with-param name="delimiter" select="'.'"></xsl:with-param>
                     </xsl:call-template>
                </xsl:variable>
                <li><a href="javascript:change('{@source}');">
                    <xsl:value-of select="substring(@source, $last-index+1)"/>
                </a></li>
            </xsl:for-each>
        </ul>
    </xsl:template> 



    <!--
    Generates the data part.
    --> 
    <xsl:template match="checkstyle" mode="data">
        <div id="content">
            <xsl:for-each select="file/error[generate-id() = generate-id(key('module',@source))]">
                <xsl:sort select="@source"/>
                <div class="hideable" id="{@source}">
                    <xsl:variable name="module" select="@source"/>
                    <h2><xsl:value-of select="@source"/></h2>
                    <xsl:call-template name="data">
                        <xsl:with-param name="filter" select="$module"/>
                    </xsl:call-template>
                </div>
            </xsl:for-each>
        </div>
    </xsl:template> 



    <!--
    Generates the content table for the given check module.
    @param filter full qualified module name 
    -->
    <xsl:template name="data">
        <xsl:param name="filter"/>

        <table>
            <tr>
                <th>file</th>
                <th>line</th>
                <th>severity</th>
                <th>message</th>
            </tr>
            <xsl:for-each select="/checkstyle/file">
                <xsl:choose>
                    <xsl:when test="error/@source=$filter">
                        <xsl:call-template name="data-rows">
                            <xsl:with-param name="node" select="."/>
                            <xsl:with-param name="filter" select="$filter"/>
                        </xsl:call-template>
                    </xsl:when>
                </xsl:choose>
            </xsl:for-each>
        </table>
    </xsl:template>



    <!--
    Generates the data rows for the current check module.
    Ignores errors in the current file from other modules.
    @param node the file with the errors
    @param filter full qualified module name 
    -->
    <xsl:template name="data-rows">
        <xsl:param name="node"/>
        <xsl:param name="filter"/>

        <xsl:for-each select="$node/error">
            <xsl:choose>
                <xsl:when test="@source=$filter">
                    <tr>
                        <!-- Hide the basdir. First char of the result is a path separator so remove that. -->
                        <td><xsl:value-of select="substring(substring-after($node/@name, $basedir),2)"/></td>
                        <td><xsl:value-of select="@line"/></td>
                        <td><xsl:value-of select="@severity"/></td>
                        <td><xsl:value-of select="@message"/></td>
                    </tr>
                </xsl:when>
            </xsl:choose>
        </xsl:for-each>
    </xsl:template>



    <!-- 
    Generates the CSS with the layout instructions.
    Generated so this XSL is the single source of the whole report.
    -->
    <xsl:template name="sorted.css">
        body { 
          font:normal 80% arial,helvetica,sanserif;
          color: black; 
          background-color: white; 
          margin: 0; 
          padding: 1em; 
          min-width: 41em;
        } 
        h1 { 
          font-weight:bold;
          font-size:140%;
          margin: 0 0 0.7em; 
          padding: 0.3em; 
          text-align: center; 
          background-color: #eee; 
          border: 2px ridge silver; 
        } 
        html<xsl:text disable-output-escaping="yes">&gt;</xsl:text>body h1 { 
          border-color: gray;
        } 

        ul#navigation { 
          font-size: 0.83em; 
          float: left; width: 18em; 
          margin: 0 0 1.2em; padding: 0; 
          border: 1px dashed silver; 
        } 
        ul#navigation li { 
          list-style: none; 
          margin: 0; padding: 0.2em; 
        } 
        ul#navigation a { 
          display: block; 
          padding: 0.2em; 
          font-weight: bold; 
        } 
        ul#navigation a:link { 
          color: black; background-color: #eee; 
        } 
        ul#navigation a:visited { 
          color: #666; background-color: #eee; 
        } 
        ul#navigation a:hover { 
          color: red; background-color: white; 
        } 
        ul#navigation a:active { 
          color: white; background-color: gray; 
        }

        div#content { 
          margin: 0 1em 1em 16em; 
          padding: 0 1em; 
        } 
        * html div#content { 
          height: 1em;  /* Workaround 3-Pixel-Bug of Internet Explorers */ 
        } 
        div#content h2 { 
          font-size:100%;
          font-weight:bold;
          background: #525D76;
          color: white;
          text-decoration: none;
          padding: 5px;
          margin-right: 2px;
          margin-left: 2px;
          margin-bottom: 0;
        } 
        div#content p { 
          font-size: 1em; 
          margin: 1em 0; 
        } 
        table {
          width:100%;
          border-collapse:collapse;
        }
        table td, table th {
          border:1px solid #000;
          padding:3px 7px 2px 7px;
        }
        table th {
          font-weight:bold;
          background: #ccc;
          color: black;
        }
        table tr:nth-child(odd) td {
          background: #efefef;
        }
        table tr:nth-child(even) td {
          background: #fff;
        }
    </xsl:template> 



    <!-- 
    Generates the JavaScript for the dynamic style. 
    Generated so this XSL is the single source of the whole report.
    -->
    <xsl:template name="switch.js">
        /* 
         * Hides all "hideable" div-containers
         */
        function hideAll() {
          allElements = document.getElementsByTagName("div");
          for (i = 0; i <xsl:text disable-output-escaping="yes">&lt;</xsl:text> allElements.length; i++) { 
            if (allElements[i].className=="hideable") { 
              allElements[i].style.display="none"; 
            } 
          } 
          return; 
        } 

        /* 
         * Shows one div-container and hides the other.
         * @param id id of the element to show
         */
        function change(id) { 
          hideAll(); 
          e = document.getElementById(id); 
          if (e.style.display=="none") { 
            e.style.display=""; 
          } 
          window.scrollTo(0, 0); 
          return; 
        } 

        /* 
         * Shows only the first data row.
         * Used in body:onload so the user could directly see some messages.
         */
        function openFirst() {
          hideAll();
          for (i = 0; i <xsl:text disable-output-escaping="yes">&lt;</xsl:text> allElements.length; i++) { 
            if (allElements[i].className=="hideable") { 
              allElements[i].style.display="";
              return; 
            } 
          } 
          return; 
        }
    </xsl:template>



    <!--
    Calculates the index of the last occurence of a substring in a string.
    @param txt the whole string in which to search
    @delimiter the substring to search
    -->
    <xsl:template name="last-index-of">
        <xsl:param name="txt"/>
        <xsl:param name="remainder" select="$txt"/>
        <xsl:param name="delimiter" select="' '"/>

        <xsl:choose>
            <xsl:when test="contains($remainder, $delimiter)">
                <xsl:call-template name="last-index-of">
                    <xsl:with-param name="txt" select="$txt"/>
                    <xsl:with-param name="remainder" select="substring-after($remainder, $delimiter)"/>
                    <xsl:with-param name="delimiter" select="$delimiter"/>
                </xsl:call-template>
            </xsl:when>
            <xsl:otherwise>
                <xsl:variable name="lastIndex" select="string-length(substring($txt, 1, string-length($txt)-string-length($remainder)))+1"/>
                <xsl:choose>
                    <xsl:when test="string-length($remainder)=0">
                        <xsl:value-of select="string-length($txt)"/>
                    </xsl:when>
                    <xsl:when test="$lastIndex>0">
                        <xsl:value-of select="($lastIndex - string-length($delimiter))"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:value-of select="0"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
