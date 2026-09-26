#!/bin/bash

#"mps/mps-platform/out/jetbrains mps/dist.all/lib/modules"
MODULES_DIR=$1
echo "Creating module descriptors file in ${MODULES_DIR}"

# Output file
OUTPUT_FILE="${MODULES_DIR}/module-descriptors.xml"

# Clear output file if it exists
: > "$OUTPUT_FILE"

echo "<content>" >> "$OUTPUT_FILE"

for moduleFile in "$MODULES_DIR"/*.jar; do
    if [ -f "$moduleFile" ]; then
        base_name=$(basename "$moduleFile" .jar)
        xml_file="${base_name}.xml"

        # Check if the XML file exists inside the ZIP
        if unzip -l "${moduleFile}" | awk '{print $4}' | grep -q "^$xml_file$"; then
            echo "Extracting ${xml_file} from ${moduleFile}..."
            echo "<module name=\"${base_name}\"><![CDATA[" >> "$OUTPUT_FILE"
            unzip -p "${moduleFile}" "${xml_file}" >> "$OUTPUT_FILE"
            echo "]]></module>" >> "$OUTPUT_FILE"
            echo -e "\n\n" >> "$OUTPUT_FILE"
        else
            echo "Skipping ${moduleFile}: No ${xml_file} file found."
        fi
    fi
done

echo "</content>" >> "$OUTPUT_FILE"

echo "Merged descriptors content into $OUTPUT_FILE"
