#include <Windows.h>
#include <elevTools.h>

// Author: Ilya Kazakevich


// TODO: Build as static lib instead of "shared" project to prevent uselss compilation?
void ElevAddStringToCommandLine(_Inout_ size_t* pchCurrentBufferSize, _Inout_ WCHAR** psCommandLine, _In_ WCHAR* sStringToAdd)
{
	// TODO: Doc suboptimal. Use line length instead of wcslen(*psCommandLine) ("shlemiel the painter algorithm")

	size_t nchCurrentLineSize = wcslen(*psCommandLine);
	size_t nchNewStringSize = wcslen(sStringToAdd);
	size_t nchSpaceLeftInBuffer = (*pchCurrentBufferSize) - nchCurrentLineSize;
	size_t nchRequredSize = (*pchCurrentBufferSize) + 4 + nchNewStringSize;
	if (nchSpaceLeftInBuffer < nchRequredSize)
	{
		// Not enough space, add more space
		(*pchCurrentBufferSize) = nchRequredSize;
		*psCommandLine = realloc(*psCommandLine, sizeof(WCHAR) * (*pchCurrentBufferSize));
	}
	wsprintf((*psCommandLine) + nchCurrentLineSize, L"\"%ls\" ", sStringToAdd);
}